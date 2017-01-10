(ns components.db
  (:require [clojure.string :as str]))

(def adapter-fns (atom {}))

(defprotocol RelationalDatabase
  (execute-command! [db sql-command])
  (query-database [db sql-query]))

(defn connect-to [ & {:as connection-params}]
  (fn [params]
    (if (:mocked params)
      ((:sqlite @adapter-fns) {:file ":memory:"})
      ((get @adapter-fns (:adapter connection-params) connection-params params)))))

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
