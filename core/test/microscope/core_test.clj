(ns microscope.core-test
  (:require [midje.sweet :refer :all]
            [microscope.core :as components]
            [microscope.io :as io]
            [microscope.future :as future]
            [microscope.logging :as log]
            [microscope.healthcheck :as health]
            [finagle-clojure.http.client :as http-client]
            [finagle-clojure.http.message :as msg]
            [cheshire.core :as json]))

(defn fake-component [other]
  (let [fn (atom nil)]
    (reify io/IO
      (send! [_ msg] (@fn msg))
      (listen [_ f] (reset! fn f))
      (ack! [_ msg] (io/send! other {:ack msg}))
      (reject! [_ msg ex] (io/send! other {:reject msg})))))

(def log-output (atom nil))
(defn logger [{:keys [cid]}]
  (reify
    log/Log
    (log [_ msg type data]
         (reset! log-output {:msg msg :type type :data (assoc data :cid cid)}))))

(facts "when subscribing for new messages"
  (let [last-msg (atom nil)
        other (fake-component nil)
        component (fake-component other)
        component-gen (fn [_] component)
        subscribe (components/subscribe-with :logger logger
                                             :queue component-gen)]

    (io/listen other (fn [msg] (reset! last-msg msg)))

    (fact "sends an ACK when messages were processed"
      (subscribe :queue (fn [a _] a))
      (io/send! component "some-msg")
      @last-msg => {:ack "some-msg"})

    (fact "sends a REJECT when messages fail"
      (subscribe :queue (fn [f _] (future/map #(Integer/parseInt %) f)))
      (io/send! component "ten")
      @last-msg => {:reject "ten"})

    (fact "logs CID when using default logger"
      (do
        (subscribe :queue (fn [data {:keys [logger]}]
                            (log/info logger "Foo")
                            data))
        (io/send! component "some-msg")
        @log-output) => {:msg "Foo", :type :info, :data {:cid "FOOBAR"}}
      (provided
        (components/generate-cid nil) => "FOOBAR"))

    (fact "logs when processing a message"
      (subscribe :queue (fn [a _] a))
      (io/send! component "some-msg")
      @log-output => (contains {:msg "Processing message",
                                :type :info,
                                :data (contains {:msg "some-msg"})}))

    (fact "logs an error using logger and CID to correlate things"
      (subscribe :queue (fn [f _] (future/map #(Integer/parseInt %) f)))
      (io/send! component "ten")
      @log-output => (contains {:type :fatal, :data (contains {:cid string?
                                                               :ex anything})}))))

(fact "generates a healthcheck HTTP entrypoint"
  (let [last-msg (atom nil)
        unhealthy-component (reify health/Healthcheck (unhealthy? [_] {:yes "I am"}))
        subscribe (components/subscribe-with :unhealthy (constantly unhealthy-component))
        http (http-client/service ":8081")
        _ (subscribe :healthcheck health/handle-healthcheck)
        res (-> http
                (finagle-clojure.service/apply (msg/request "/"))
                finagle-clojure.futures/await)]
    (-> res msg/content-string (json/decode true)) => {:result false
                                                       :details {:unhealthy {:yes "I am"}}}
    (msg/status-code res) => 503)
  (background
    (after :facts (health/stop-health-checker!))))

; Mocking section
(def queue (atom nil))
(def initial-state (atom nil))
(defn fake-queue [{:keys [mocked initial-state-val]}]
  (when mocked
    (reset! initial-state initial-state-val)
    (let [atom (atom nil)]
      (reset! queue
              (reify io/IO
                (listen [component function] (add-watch atom :obs (fn [_ _ _ value]
                                                                    (function {:payload value}))))
                (send! [component message] (reset! atom message))
                (ack! [component param])
                (reject! [component param ex]))))))

(defn a-function []
  (let [subscribe (components/subscribe-with :logger log/default-logger-gen
                                             :fake-queue fake-queue)]
    (subscribe :fake-queue
               (fn [future-val {:keys [logger]}]
                 (future/map (fn [_] (log/error logger "Message")) future-val)))))

(facts "When testing code"
  (fact "uses mocked components only"
    (components/mocked
     (a-function)
     (with-out-str
       (io/send! @queue {:payload "Some msg"})) => #"ERROR: Message\n\n\{:cid"))

  (fact "allows to pass parameters to mocked env"
    (components/mocked {:initial-state-val :some-initial-state}
     (a-function)
     @initial-state => :some-initial-state))

  (fact "allows to define the mocked obj"
    (components/mocked {:mocks {:fake-queue (fn [_]
                                              (reset! initial-state :mocked!)
                                              (reify io/IO (listen [_ _])))}}

      (a-function)
      @initial-state => :mocked!)))
