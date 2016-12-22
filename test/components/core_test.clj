(ns components.core-test
  (:require [midje.sweet :refer :all]
            [components.core :as components]
            [components.future :as future]
            [components.logging :as log]
            [components.cid :as cid]))

(defn fake-component [other]
  (let [fn (atom nil)]
    (reify components/IO
      (send! [_ msg] (@fn msg))
      (listen [_ f] (reset! fn f))
      (ack! [_ msg] (components/send! other {:ack msg}))
      (reject! [_ msg ex] (components/send! other {:reject msg})))))

(def log-output (atom nil))
(def logger
  (reify
    log/Log
    (log [_ msg type data]
         (reset! log-output {:msg msg :type type :data data}))
    cid/CID
    (append-cid [l cid] (log/decorate-logger l cid))))

(def subscribe (components/subscribe-with
                 :logger logger))

(facts "when subscribing for new messages"
  (let [last-msg (atom nil)
        other (fake-component nil)
        component (fake-component other)]
    (components/listen other (fn [msg] (reset! last-msg msg)))

    (fact "sends an ACK when messages were processed"
      (subscribe component (fn [a _] a))
      (components/send! component "some-msg")
      @last-msg => {:ack "some-msg"})

    (fact "sends a REJECT when messages fail"
      (subscribe component (fn [f _] (future/map #(Integer/parseInt %) f)))
      (components/send! component "ten")
      @last-msg => {:reject "ten"})

    (fact "logs CID when using default logger"
      (do
        (subscribe component (fn [data {:keys [logger]}]
                               (log/info logger "Foo")
                               data))
        (components/send! component "some-msg")
        @log-output) => {:msg "Foo", :type :info, :data {:cid "FOOBAR"}}
      (provided
        (components/generate-cid nil) => "FOOBAR"))

    (fact "logs when processing a message"
      (subscribe component (fn [a _] a))
      (components/send! component "some-msg")
      @log-output => (contains {:msg "Processing message",
                                :type :info,
                                :data (contains {:msg "some-msg"})}))

    (fact "logs an error using logger and CID to correlate things"
      (subscribe component (fn [f _] (future/map #(Integer/parseInt %) f)))
      (components/send! component "ten")
      @log-output => (contains {:type :fatal, :data (contains {:cid string?
                                                               :ex anything})}))))
