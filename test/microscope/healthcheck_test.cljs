(ns microscope.healthcheck-test
  (:require [clojure.test :refer-macros [deftest is testing run-tests async]]
            [microscope.healthcheck :as health]))

(def unhealthy-async-component
  (reify health/Healthcheck
    (unhealthy? [_] (. js/Promise resolve {:channel "isn't connected"}))))

(def healthy-async-component
  (reify health/Healthcheck
    (unhealthy? [_] (. js/Promise resolve nil))))

(deftest asynchronous-healthcheck-is-healthy
  (async done
    (.then (health/check {:alive healthy-async-component})
           #(do
              (is (= {:result true :details {:alive nil}} %))
              (done)))))

(deftest asynchronous-healthcheck-is-unhealthy
  (async done
    (.then (health/check {:alive healthy-async-component
                          :dead unhealthy-async-component
                          :other "foo"})
           #(do
              (is (= {:result false :details {:dead {:channel "isn't connected"}
                                              :alive nil}}
                     %))
              (done)))))

(def unhealthy-component
  (reify health/Healthcheck
    (unhealthy? [_] {:channel "isn't connected"})))

(def healthy-component
  (reify health/Healthcheck
    (unhealthy? [_] nil)))

(deftest synchronous-healthcheck-is-unhealthy
  (async done
    (.then (health/check {:alive healthy-component
                          :dead unhealthy-component
                          :other "foo"})
           #(do
              (is (= {:result false :details {:dead {:channel "isn't connected"}
                                              :alive nil}})
                  %)
              (done)))))

(run-tests)
