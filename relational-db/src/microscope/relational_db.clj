(ns microscope.relational-db
  (:require [microscope.healthcheck :as health]
            [clojure.java.jdbc :as jdbc])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))
  ;          [javax.sql DataSource PooledConnection]
  ;          [java.sql DriverManager]))

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

(defmacro gen-constructor [code]
  `(fn [params#]))
