(ns microscope.relational-db-test
  (:require [midje.sweet :refer :all]
            [microscope.relational-db :as db]
            [microscope.relational-db.sqlite :as sqlite]
            [microscope.healthcheck :as health]
            [clojure.java.jdbc :as jdbc]))

(fact "creates a connection pooled database"
  (let [pool (db/db-for "org.sqlite.JDBC" "jdbc:sqlite::memory:" nil nil)]
    (jdbc/query pool "SELECT 'foo' as bar") => [{:bar "foo"}]))

(fact "wraps connection pool in a service constructor"
  (let [constructor (gen-constructor
                     (db/db-for "org.sqlite.JDBC" "jdbc:sqlite::memory:" nil nil))]
    (jdbc/query (pool {}) "SELECT 'foo' as bar") => [{:bar "foo"}]))

(facts "about healthcheck"
  (let [db (sqlite/memory nil)]
    (fact "healthchecks when DB is connected"
      (health/check {:db db}) => {:result true :details {:db nil}})

    (fact "fails with connection error when DB is disconnected"
      (.close (:datasource db))
      (health/check {:db db}) => {:result false
                                  :details {:db {:connection "failed simple select"}}})

    (fact "fails with exception message when something REALLY STRANGE occurred"
      (health/check {:db db}) => {:result false
                                  :details {:db {:connection "unknown error"
                                                 :exception-type "clojure.lang.ExceptionInfo"
                                                 :exception-msg "strange error"}}}
      (provided
       (jdbc/query irrelevant irrelevant) =throws=> (ex-info "strange error" {})))))
