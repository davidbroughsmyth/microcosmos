(ns microscope.env-test
  (:require [midje.sweet :refer :all]
            [microscope.env :refer [secret-or-env]]
            [environ.core :as environ]))

(facts "about `secret-or-env`"
  (fact "when a secret file is present"
    (secret-or-env :db-password "test/fixtures/secrets") => "secret db password")

  (fact "when an env variable is present"
    (secret-or-env :rabbit-password "test/fixtures/secrets") => "secret rabbit password"
    (provided
      (environ/env :rabbit-password) => "secret rabbit password")))
