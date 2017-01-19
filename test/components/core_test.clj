(ns components.core-test
  (:require [midje.sweet :refer :all]
            [components.core :as components]
            [components.future :as future]
            [components.logging :as log]
            [components.healthcheck :as health]
            [finagle-clojure.http.client :as http-client]))

(defn fake-component [other]
  (let [fn (atom nil)]
    (reify components/IO
      (send! [_ msg] (@fn msg))
      (listen [_ f] (reset! fn f))
      (ack! [_ msg] (components/send! other {:ack msg}))
      (reject! [_ msg ex] (components/send! other {:reject msg})))))

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

    (components/listen other (fn [msg] (reset! last-msg msg)))

    (fact "sends an ACK when messages were processed"
      (subscribe :queue (fn [a _] a))
      (components/send! component "some-msg")
      @last-msg => {:ack "some-msg"})

    (fact "sends a REJECT when messages fail"
      (subscribe :queue (fn [f _] (future/map #(Integer/parseInt %) f)))
      (components/send! component "ten")
      @last-msg => {:reject "ten"})

    (fact "logs CID when using default logger"
      (do
        (subscribe :queue (fn [data {:keys [logger]}]
                            (log/info logger "Foo")
                            data))
        (components/send! component "some-msg")
        @log-output) => {:msg "Foo", :type :info, :data {:cid "FOOBAR"}}
      (provided
        (components/generate-cid nil) => "FOOBAR"))

    (fact "logs when processing a message"
      (subscribe :queue (fn [a _] a))
      (components/send! component "some-msg")
      @log-output => (contains {:msg "Processing message",
                                :type :info,
                                :data (contains {:msg "some-msg"})}))

    (fact "logs an error using logger and CID to correlate things"
      (subscribe :queue (fn [f _] (future/map #(Integer/parseInt %) f)))
      (components/send! component "ten")
      @log-output => (contains {:type :fatal, :data (contains {:cid string?
                                                               :ex anything})}))))

(fact "generates a healthcheck HTTP entrypoint"
  (let [last-msg (atom nil)
        other (fake-component nil)
        component (fake-component other)
        component-gen (fn [_] component)
        unhealthy-component (reify health/Healthcheck (unhealthy? [_] {:yes "I am"}))
        subscribe (components/subscribe-with :unhealthy (constantly unhealthy-component)
                                             :queue component-gen)]
    (subscribe :queue (constantly nil))))
    ; ()))

; Mocking section
(def queue (atom nil))
(def initial-state (atom nil))
(defn fake-queue [{:keys [mocked initial-state-val]}]
  (when mocked
    (reset! initial-state initial-state-val)
    (let [atom (atom nil)]
      (reset! queue
              (reify components/IO
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
       (components/send! @queue {:payload "Some msg"})) => #"ERROR: Message\n\n\{:cid"))

  (fact "allows to pass parameters to mocked env"
    (components/mocked {:initial-state-val :some-initial-state}
     (a-function)
     @initial-state => :some-initial-state)))
