(ns components.db.sqlite-test
  (:require [midje.sweet :refer :all]
            [components.core :as components]
            [components.db :as db]
            [components.db.sqlite :as sqlite]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]))

; (defn mocked-db [db])
;
;
; (def teardowns (atom []))
; (defn teardown [f] (swap! teardowns conj f))

(facts "about sqlite environment"
  ; (let [db (sqlite/adapter {:file "__test__.db"} {:teardown teardown})])
  (let [db (sqlite/adapter {:file "__test__.db"} {})]
    ; (fact "adds a teardown fn"
    ;   @teardowns => not-empty)
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
        ; (db/execute! db1 "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
        (db/execute! db2 "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
        (db/execute! db2 "INSERT INTO foo VALUES (:id, :name)" {:id "foo" :name "bar"}) => [1]
        (db/query db1 "SELECT * FROM foo") => [])))

  ; (fact "creates a database in memory, preparing with some queries")

  (background
    (after :facts (do
                    (io/delete-file "__test__.db" true)
                    (reset! sqlite/all-pools {})))))
                    ; (doseq [t @teardowns] (t))
                    ; (reset! teardowns [])))))
                    ; (reset! sqlite/prepare-mem-db)))))
; (slurp "__test__.db")
