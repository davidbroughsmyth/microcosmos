(ns components.db
  (:require [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]))

(def adapter-fns (atom {}))

(defprotocol RelationalDatabase
  (execute-command! [db sql-command])
  (query-database [db sql-query])
  (get-jdbc-connection [db])
  (using-jdbc-connection [db conn]))

(defn connect-to [ & {:as connection-params}]
  (fn [params]
    (if (:mocked params)
      ((:sqlite @adapter-fns) {:file ":memory:"} params)
      ((get @adapter-fns (:adapter connection-params)) connection-params params))))

(defn- quote-regex [s]
  (-> s str clojure.string/re-quote-replacement (str/replace #"\." "\\\\.")))

(defn normalize [sql params]
  (let [re (->> params
                keys
                (map quote-regex)
                (str/join "|")
                re-pattern)
        matches (re-seq re sql)]
    (->> matches
         (map #(get params (keyword (str/replace-first % #":" ""))))
         (cons (str/replace sql re "?"))
         vec)))

(defn execute!
  ([db sql-command] (execute-command! db [sql-command]))
  ([db sql-command params] (execute-command! db (normalize sql-command params))))

(defn query
  ([db sql-command] (query-database db [sql-command]))
  ([db sql-command params] (query-database db (normalize sql-command params))))

(defmacro transaction [db & body]
  `(jdbc/with-db-transaction [db# (get-jdbc-connection ~db)]
     (let [~db (using-jdbc-connection ~db db#)]
       ~@body)))

(defn rollback! [db]
  (execute! db "ROLLBACK")
  (execute! db "BEGIN"))

(defn fake-rows
  "Generates an in-memory database, prepared by `prepare-fn`, and with some rows
already populated. The database will be created, populated by `tables-and-rows`, and
then returned as a connection ready to make modifications.

Usage example:

(let [db (fake-rows #(db/execute! \"CREATE TABLE example (name VARCHAR(255))\"))
                    {:example [{:name \"foo\"} {:name \"bar\"}]}]
  (db/query \"SELECT * FROM example\"))"
  [prepare-fn tables-and-rows]
  (let [conn-factory (connect-to :adapter :sqlite3)
        db (conn-factory {:mocked true :setup-db-fn prepare-fn})]
    (doseq [[table rows] tables-and-rows
            row rows
            :let [fields (map name (keys row))]]
      (execute! db
                (str "INSERT INTO " (name table)
                     "(" (str/join "," fields) ")"
                     " VALUES(" (str/join "," (keys row)) ")")
                row))
    db))
