(ns microscope.relational-db.hsqldb-test
  (:require [midje.sweet :refer :all]
            [microscope.relational-db.hsqldb :as hsqldb]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]))

(facts "about hsqldb environment"
  (let [db ((hsqldb/db (str "mem:" (rand))) {})]
    (fact "can execute queries that have no results"
      (jdbc/execute! db "CREATE TABLE foo (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
      (jdbc/execute! db ["INSERT INTO foo VALUES (?, ?)" "foo" "bar"]) => [1])

    (fact "can query database"
      (jdbc/query db ["SELECT * FROM foo WHERE id=?" "foo"]) => [{:id "foo" :name "bar"}]
      (jdbc/query db ["SELECT * FROM foo WHERE id=?" "bar"]) => []))

  (background
    (after :facts (do
                    (io/delete-file "__test__.db" true)))))
