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
    (jdbc/query conn sql-command))

  (get-jdbc-connection [db] conn)
  (using-jdbc-connection [db conn] (assoc db :conn conn)))

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

(defn new-memory-db [setup-db-fn]
  (let [pool (doto (create-pool ":memory:")
                   (.setMaxPoolSize 1)
                   (.setMinPoolSize 1)
                   (.setInitialPoolSize 1))
        db (->SQLite {:datasource pool})]
    (when setup-db-fn (setup-db-fn db))
    db))

(defn adapter [{:keys [file]} {:keys [setup-db-fn]}]
  (if (= ":memory:" file)
    (new-memory-db setup-db-fn)
    (->SQLite {:datasource (pool file)})))

(swap! db/adapter-fns assoc :sqlite adapter)
