(ns microscope.relational-db.sqlite-test
  (:require [midje.sweet :refer :all]
            [microscope.relational-db.sqlite :as sqlite]
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
