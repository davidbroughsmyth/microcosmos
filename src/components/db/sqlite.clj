(ns components.db.sqlite
  (:require [clojure.java.jdbc :as jdbc]
            [components.db :as db])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]
           [javax.sql DataSource PooledConnection]
           [java.sql DriverManager]))


(defrecord SQLite [conn]
  db/RelationalDatabase
  (execute-command! [s sql-command]
    (jdbc/execute! conn sql-command))

  (query-database [s sql-command]
    (jdbc/query conn sql-command)))

(defn- create-pool [file-name]
  (doto (ComboPooledDataSource.)
        (.setDriverClass "org.sqlite.JDBC")
        (.setJdbcUrl (str "jdbc:sqlite:" file-name))
        (.setUser nil)
        (.setPassword nil)
        ;; expire excess connections after 30 minutes of inactivity:
        (.setMaxIdleTimeExcessConnections (* 30 60))
        ;; expire connections after 3 hours of inactivity:
        (.setMaxIdleTime (* 3 60 60))))

(def all-pools (atom {}))
(defn pool [file]
  (or (get @all-pools file)
      (let [pool (create-pool file)]
        (swap! all-pools assoc file pool)
        pool)))

(defn new-memory-db []
  (let [pool (create-pool ":memory:")]
    (.setMaxPoolSize pool 1)
    (.setMinPoolSize pool 1)
    (.setInitialPoolSize pool 1)
    (->SQLite {:datasource pool})))

(defn adapter [{:keys [file]} {:keys [teardown]}]
  (if (= ":memory:" file)
    (new-memory-db)
    (->SQLite {:datasource (pool file)})))

(swap! db/adapter-fns assoc :sqlite adapter)
; ;
; (jdbc/execute! {:datasource (pool ":memory:")}
;                ["CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY)"])
; ;
; (jdbc/execute! {:datasource (pool ":memory:")}
;                ["INSERT INTO foo VALUES(:name)" "bar"])
; ;
; (jdbc/query {:datasource (pool ":memory:")}
;             ["SELECT * FROM foo WHERE id=:foo OR id < :id OR id > :id"
;              "bar"])

; (.getConnection (pool ":memory:"))
; (NewProxyConnection. (.getConnection (pool ":memory:")))
