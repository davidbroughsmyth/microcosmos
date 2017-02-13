(ns microscope.relational-db-test
  (:require [midje.sweet :refer :all]
            [microscope.relational-db :as db]
            [microscope.relational-db.sqlite :as sqlite]
            [microscope.healthcheck :as health]
            [clojure.java.jdbc :as jdbc]))

(fact "creates a connection pooled database"
  (let [pool (db/db-for "org.sqlite.JDBC" "jdbc:sqlite::memory:" nil nil)]
    (jdbc/query pool "SELECT 'foo' as bar") => [{:bar "foo"}]))


(defn mocked-db [db]
  (jdbc/execute! db "CREATE TABLE tests (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
  (jdbc/execute! db ["INSERT INTO tests VALUES (?, ?)" "foo" "bar"]))

(facts "about mocked environment"
  (fact "defines a memory database, with pool size=1"
    (let [db (db/sqlite-memory mocked-db)]
      (jdbc/query db "SELECT * FROM tests") => [{:id "foo" :name "bar"}]))

  (facts "about memory database"
    (fact "uses a different DB everytime we need one"
      (let [db1 (db/sqlite-memory nil)
            db2 (db/sqlite-memory nil)]
        (jdbc/execute! db1 "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
        (jdbc/execute! db2 "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
        (jdbc/execute! db2 ["INSERT INTO foo VALUES (:id, :name)" "foo" "bar"]) => [1]
        (jdbc/query db1 "SELECT * FROM foo") => []))))

(fact "wraps connection pool in a service constructor"
  (let [c1 (db/gen-constructor (db/sqlite-memory nil))
        c2 (db/gen-constructor (db/sqlite-memory nil))]
    (jdbc/execute! (c1 {})
                   "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
    (jdbc/execute! (c2 {})
                   "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")

    (fact "second calls to constructor will reuse pool"
      (jdbc/execute! (c1 {})
                     ["INSERT INTO foo VALUES (:id, :name)" "foo" "bar"]) => [1]
      (jdbc/query (c2 {}) "SELECT * FROM foo") => []))

  (fact "will return a memory DB if mocked"
    (let [constructor (db/gen-constructor (db/db-for "foo" "bar" "" ""))]
      (jdbc/query (constructor {:mocked true
                                :setup-db-fn mocked-db})
                  "SELECT * FROM tests") => [{:id "foo" :name "bar"}])))

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