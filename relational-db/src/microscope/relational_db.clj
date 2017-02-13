(ns microscope.relational-db
  (:require [microscope.healthcheck :as health]
            [clojure.java.jdbc :as jdbc])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defrecord Database [datasource]
  health/Healthcheck
  (unhealthy? [self]
    (let [[result] (try (jdbc/query self "SELECT 'ok' as ok ")
                     (catch java.sql.SQLException _ [])
                     (catch Exception e [e]))]
      (case result
        {:ok "ok"} nil
        nil {:connection "failed simple select"}
        {:connection "unknown error"
         :exception-type (.getName (.getClass result))
         :exception-msg (.getMessage result)}))))

(defn db-for [driver url username password]
  (let [pool (doto (ComboPooledDataSource.)
                   (.setDriverClass driver)
                   (.setJdbcUrl url)
                   (.setUser username)
                   (.setPassword password)
                   (.setMaxIdleTimeExcessConnections (* 30 60))
                   (.setMaxIdleTime (* 3 60 60)))]
    (->Database pool)))

(defn sqlite-memory [setup-db-fn]
  (let [db (db-for "org.sqlite.JDBC" "jdbc:sqlite::memory:" nil nil)
        pool (doto (:datasource db)
                   (.setMaxPoolSize 1)
                   (.setMinPoolSize 1)
                   (.setInitialPoolSize 1))]
    (when setup-db-fn (setup-db-fn db))
    db))

(defmacro gen-constructor [code]
  `(let [pool# (delay ~code)]
     (fn [params#]
       (if (:mocked params#)
         (sqlite-memory (:setup-db-fn params#))
         @pool#))))
