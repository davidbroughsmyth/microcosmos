(ns components.healthcheck-test
  (:require [midje.sweet :refer :all]
            [components.healthcheck :as health]))

(def unhealthy-component
  (reify health/Healthcheck
    (unhealthy? [_] {:channel "isn't connected"})))

(def healthy-component
  (reify health/Healthcheck
    (unhealthy? [_] nil)))

(facts "about healthcheck"
  (fact "marks full stack as unhealthy if one of components' healthcheck fails"
    (health/check {:alive healthy-component :dead unhealthy-component :other "foo"})
    => {:result false :details {:dead {:channel "isn't connected"}
                                :alive nil}}))
