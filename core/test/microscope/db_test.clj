(ns microscope.db-test
  (:require [midje.sweet :refer :all]
            [microscope.db :as db]))

(defn mocked-db [db]
  (db/execute! db "CREATE TABLE tests (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))")
  (db/execute! db "CREATE TABLE t2 (id VARCHAR(255) PRIMARY KEY,
                                    name VARCHAR(255),
                                    age INTEGER)"))

(facts "normalizing queries"
  (fact "translates queries with params to JDBC default"
    (db/normalize (str "SELECT * FROM foo WHERE id=:id AND name=:some-name-10 "
                       "OR uuid=:id") {:id 10 :some-name-10 "bar"})
    => ["SELECT * FROM foo WHERE id=? AND name=? OR uuid=?" 10 "bar" 10]))

(facts "about database connections"
  (fact "creates simple memory db data for unit tests"
    (let [db (db/fake-rows mocked-db {:tests [{:id "foo" :name "bar"}
                                              {:id "bar" :name "baz"}]})]
      (db/query db "SELECT * FROM tests WHERE id=:id" {:id "foo"}) => [{:id "foo" :name "bar"}]))

  (fact "inserts data into DB"
    (let [db (db/fake-rows mocked-db {})]
      (db/insert! db "tests" {:id "nothing" :name "at all"})
      (db/query db "SELECT * FROM tests") => [{:id "nothing" :name "at all"}]))

  (fact "updates data in DB"
    (let [db (db/fake-rows mocked-db {:tests [{:id "foo" :name "bar"}
                                              {:id "bar" :name "baz"}]})]
      (db/update! db "tests" {:name "woo"} {:name "bar"})
      (db/query db "SELECT * FROM tests ORDER BY id") => [{:id "bar" :name "baz"}
                                                          {:id "foo" :name "woo"}]))

  (fact "inserts or updates, depending on case"
    (let [db (db/fake-rows mocked-db {:t2 [{:id "foo" :name "bar" :age 30}]})]
      (db/upsert! db "t2" :name {:age 50 :name "bar"})
      (db/query db "SELECT * FROM t2 ORDER BY id") => [{:id "foo" :name "bar" :age 50}]
      (db/upsert! db "t2" :id {:id "fooa" :age 40 :name "bar"})
      (db/query db "SELECT * FROM t2 ORDER BY id") => [{:id "foo" :name "bar" :age 50}
                                                       {:id "fooa" :name "bar" :age 40}]
      (db/upsert! db "t2" :name {:id "foo" :name "bar"})
      => (throws clojure.lang.ExceptionInfo)
      (fact "don't change ID"
        (db/upsert! db "t2" :age {:id "sbrubles" :name "bar" :age 40})
        (db/query db "SELECT * FROM t2 ORDER BY id") => [{:id "foo" :name "bar" :age 50}
                                                         {:id "fooa" :name "bar" :age 40}])))

  (fact "allow transactions"
    (let [db (db/fake-rows mocked-db {:tests [{:id "foo" :name "bar"}]})]
      (db/transaction db
        (db/execute! db "UPDATE tests SET name=:name" {:name "test"})
        (db/query db "SELECT * FROM tests") => [{:id "foo" :name "test"}]
        (db/rollback! db))
      (db/query db "SELECT * FROM tests") => [{:id "foo" :name "bar"}])))
