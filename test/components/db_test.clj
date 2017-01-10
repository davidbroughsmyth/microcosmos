(ns components.db-test
  (:require [midje.sweet :refer :all]
            [components.db :as db]))

(defn mocked-db [db]
  (db/execute! db "CREATE TABLE tests (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))"))

(facts "normalizing queries"
  (fact "translates queries with params to JDBC default"
    (db/normalize (str "SELECT * FROM foo WHERE id=:id AND name=:some-name-10 "
                       "OR uuid=:id") {:id 10 :some-name-10 "bar"})
    => ["SELECT * FROM foo WHERE id=? AND name=? OR uuid=?" 10 "bar" 10]))

(facts "about database connections"
  (fact "creates simple memory db data for unit tests"
    (db/let-rows mocked-db
                 {:tests [{:id "foo" :name "bar"}
                          {:id "bar" :name "baz"}]}
                 db
      (db/query db "SELECT * FROM tests WHERE id=:id" {:id "foo"}) => [{:id "foo" :name "bar"}]))

  (fact "allow transactions"
    (db/let-rows mocked-db {:tests [{:id "foo" :name "bar"}]} db
      (db/transaction db
        (db/execute! db "UPDATE tests SET name=:name" {:name "test"})
        (db/query db "SELECT * FROM tests") => [{:id "foo" :name "test"}]
        (db/rollback! db))
      (db/query db "SELECT * FROM tests") => [{:id "foo" :name "bar"}])))
