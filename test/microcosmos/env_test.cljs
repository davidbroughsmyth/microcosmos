(ns microcosmos.env-test
  (:require [clojure.test :refer-macros [deftest is testing run-tests async]]
            [microcosmos.env :refer [secret-or-env]]))

(deftest secret-or-env-test
  (testing "when a secret file is present"
    (is (= "secret db password"
           (secret-or-env :db-password "test/fixtures/secrets"))))

  (testing "when an env variable is present"
    (aset (.-env js/process) "RABBIT_PASSWORD" "secret rabbit password")
    (is  (= "secret rabbit password"
            (secret-or-env :rabbit-password "test/fixtures/secrets")))))
