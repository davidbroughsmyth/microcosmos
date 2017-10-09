(ns microscope.healthcheck-test
  (:require [clojure.test :refer-macros [deftest is testing run-tests async]]
            [microscope.core :as components]
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

(def http (js/require "http"))
(defn get-json [fun]
  (let [output (atom "")
        json (atom nil)
        req (. http (request #js {:host "localhost" :port 808 :path "/health" :method "GET"}
                            (fn [response]
                              (doto response
                                    (.on "data" #(swap! output str %))
                                    (.on "end" #(reset! json (->> @output
                                                                  (.parse js/JSON)
                                                                  (js->clj)
                                                                  (assoc :status-code (.-statusCode %))
                                                                  fun)))
                                    (.on "error" println)))))]
    (.end req)))

(deftest gen-http-entrypoint
  (async done
    (let [subscribe (components/subscribe-with :healthy (constantly healthy-component))]
      (subscribe :healthcheck health/handle-healthcheck)
      (get-json #(do
                   (is (= {:result true :details {:healthy nil} :status-code 200}
                          %))
                   (done))))))

(run-tests)
