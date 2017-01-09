(ns components.db-test
  (:require [midje.sweet :refer :all]
            [components.db :as db]))

(facts "normalizing queries"
  (fact "translates queries with params to JDBC default"
    (db/normalize (str "SELECT * FROM foo WHERE id=:id AND name=:some-name-10 "
                       "OR uuid=:id") {:id 10 :some-name-10 "bar"})
    => ["SELECT * FROM foo WHERE id=? AND name=? OR uuid=?" 10 "bar" 10]))
