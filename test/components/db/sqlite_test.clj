(ns components.db.sqlite-test
  (:require [midje.sweet :refer :all]
            [components.core :as components]
            [components.healthcheck :as health]
            [components.db :as db]
            [components.db.sqlite :as sqlite]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]))

(defn mocked-db [db]
  (db/execute! db "CREATE TABLE tests (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
  (db/execute! db "INSERT INTO tests VALUES (:id, :name)" {:id "foo" :name "bar"}))

(facts "about sqlite environment"
  (let [db (sqlite/adapter {:file "__test__.db"} {})]
    (fact "can execute queries that have no results"
      (db/execute! db "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
      (db/execute! db "INSERT INTO foo VALUES (:id, :name)" {:id "foo" :name "bar"}) => [1])

    (fact "can query database"
      (db/query db "SELECT * FROM foo WHERE id=:id" {:id "foo"}) => [{:id "foo" :name "bar"}]
      (db/query db "SELECT * FROM foo WHERE id=:id" {:id "bar"}) => []))

  (facts "about memory database"
    (fact "uses a different DB everytime we need one"
      (let [db1 (sqlite/adapter {:file ":memory:"} {})
            db2 (sqlite/adapter {:file ":memory:"} {})]
        (db/execute! db1 "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
        (db/execute! db2 "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
        (db/execute! db2 "INSERT INTO foo VALUES (:id, :name)" {:id "foo" :name "bar"}) => [1]
        (db/query db1 "SELECT * FROM foo") => [])))

  (fact "creates a database in memory, preparing with some commands"
    (let [db (sqlite/adapter {:file ":memory:"} {:setup-db-fn mocked-db})]
      (db/query db "SELECT * FROM tests") = [{:id "foo" :name "bar"}]>))

  (background
    (after :facts (do
                    (io/delete-file "__test__.db" true)
                    (reset! sqlite/all-pools {})))))

(facts "about healthcheck"
  (let [db (sqlite/adapter {:file ":memory:"} {})]
    (fact "healthchecks when DB is connected"
      (health/check {:db db}) => {:result true :details {:db nil}})

    (fact "fails with connection error when DB is disconnected"
      (.close (:datasource (:conn db)))
      (health/check {:db db}) => {:result false
                                  :details {:db {:connection "failed simple select"}}})

    (fact "fails with exception message when something REALLY STRANGE occurred"
      (health/check {:db db}) => {:result false
                                  :details {:db {:connection "unknown error"
                                                 :exception-type "clojure.lang.ExceptionInfo"
                                                 :exception-msg "strange error"}}}
      (provided
       (db/query irrelevant irrelevant) =throws=> (ex-info "strange error" {})))))
