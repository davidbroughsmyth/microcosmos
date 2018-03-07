(ns microcosmos.healthcheck-test
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [microcosmos.logging :as log]
            [microcosmos.core :as components]
            [finagle-clojure.http.client :as http-client]
            [finagle-clojure.http.message :as msg]
            [microcosmos.healthcheck :as health]))

(def unhealthy-component
  (reify health/Healthcheck
    (unhealthy? [_] {:channel "isn't connected"})))

(def healthy-component
  (reify health/Healthcheck
    (unhealthy? [_] nil)))

(def log-messages (atom []))
(defn logger-gen [_]
  (reset! log-messages [])
  (reify log/Log
    (log [_ message type data]
      (swap! log-messages conj {:message message :type type :data data}))))

(defn get-json []
  (let [res (-> (http-client/service ":8081")
                (finagle-clojure.service/apply (msg/request "/"))
                finagle-clojure.futures/await)]
    (-> res
        msg/content-string
        (json/decode true)
        (assoc :status-code (msg/status-code res)))))

(facts "about healthcheck"
  (fact "marks full stack as unhealthy if one of components' healthcheck fails"
    (health/check {:alive healthy-component}) => {:result true :details {:alive nil}}
    (health/check {:alive healthy-component :dead unhealthy-component :other "foo"})
    => {:result false :details {:dead {:channel "isn't connected"}
                                :alive nil}})

  (fact "generates a healthcheck HTTP entrypoint"
    (let [subscribe (components/subscribe-with :healthy (constantly healthy-component)
                                               :logger logger-gen)]
      (subscribe :healthcheck health/handle-healthcheck)
      (get-json) => {:result true :details {:healthy nil} :status-code 200}
      @log-messages => []))

  (fact "returns a status code 503 and logs error if something is unhealty"
    (let [subscribe (components/subscribe-with :unhealthy (constantly unhealthy-component)
                                               :logger logger-gen)]
      (subscribe :healthcheck health/handle-healthcheck)
      (get-json) => {:result false :details {:unhealthy {:channel "isn't connected"}}
                     :status-code 503}
      @log-messages => [{:message "Healthcheck failed" :type :fatal
                         :data {:additional-info
                                "{\"unhealthy\":{\"channel\":\"isn't connected\"}}"}}]))

  (background
    (after :facts (health/stop-health-checker!))))
