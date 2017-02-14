(ns microscope.relational-db.sqlite-test
  (:require [midje.sweet :refer :all]
            [microscope.relational-db :as db]
            [microscope.relational-db.sqlite :as sqlite]
            [microscope.healthcheck :as health]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]))

(facts "about sqlite environment"
  (let [db ((sqlite/db "__test__.db") {})]
    (fact "can execute queries that have no results"
      (jdbc/execute! db "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
      (jdbc/execute! db ["INSERT INTO foo VALUES (:id, :name)" "foo" "bar"]) => [1])

    (fact "can query database"
      (jdbc/query db ["SELECT * FROM foo WHERE id=:id" "foo"]) => [{:id "foo" :name "bar"}]
      (jdbc/query db ["SELECT * FROM foo WHERE id=:id" "bar"]) => []))

  (background
    (after :facts (do
                    (io/delete-file "__test__.db" true)))))

(facts "about healthcheck"
  (let [db (db/sqlite-memory nil)]
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
