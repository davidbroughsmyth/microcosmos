(ns components.core-test
  (:require [midje.sweet :refer :all]
            [components.core :as components]
            [components.future :as future]
            [components.logging :as log]))

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

(def subscribe (components/subscribe-with :logger logger))

(facts "when subscribing for new messages"
  (let [last-msg (atom nil)
        other (fake-component nil)
        component (fake-component other)
        component-gen (fn [_] component)]

    (components/listen other (fn [msg] (reset! last-msg msg)))

    (fact "sends an ACK when messages were processed"
      (subscribe component-gen (fn [a _] a))
      (components/send! component "some-msg")
      @last-msg => {:ack "some-msg"})

    (fact "sends a REJECT when messages fail"
      (subscribe component-gen (fn [f _] (future/map #(Integer/parseInt %) f)))
      (components/send! component "ten")
      @last-msg => {:reject "ten"})

    (fact "logs CID when using default logger"
      (do
        (subscribe component-gen (fn [data {:keys [logger]}]
                                   (log/info logger "Foo")
                                   data))
        (components/send! component "some-msg")
        @log-output) => {:msg "Foo", :type :info, :data {:cid "FOOBAR"}}
      (provided
        (components/generate-cid nil) => "FOOBAR"))

    (fact "logs when processing a message"
      (subscribe component-gen (fn [a _] a))
      (components/send! component "some-msg")
      @log-output => (contains {:msg "Processing message",
                                :type :info,
                                :data (contains {:msg "some-msg"})}))

    (fact "logs an error using logger and CID to correlate things"
      (subscribe component-gen (fn [f _] (future/map #(Integer/parseInt %) f)))
      (components/send! component "ten")
      @log-output => (contains {:type :fatal, :data (contains {:cid string?
                                                               :ex anything})}))

    (fact "allows to run some code when message processing ends"
      (let [ran (atom nil)
            subscribe (components/subscribe-with :logger logger
                                                 :comp (fn [{:keys [teardown]}]
                                                         (println "Defining TEARDOWN")
                                                         (teardown (fn []
                                                                     (reset! ran :yes)))))]
        (subscribe component-gen (fn [f _] (future/map #(Integer/parseInt %) f)))
        (components/send! component "ten")
        @ran => :yes))))

; Mocking section
(def queue (atom nil))
(defn fake-queue [{:keys [mocked]}]
  (when mocked
    (let [atom (atom nil)]
      (reset! queue
              (reify components/IO
                (listen [component function] (add-watch atom :obs (fn [_ _ _ value]
                                                                    (function {:payload value}))))
                (send! [component message] (reset! atom message))
                (ack! [component param])
                (reject! [component param ex]))))))

(defn a-function []
  (let [subscribe (components/subscribe-with :logger log/default-logger-gen)]
    (subscribe fake-queue
               (fn [future-val {:keys [logger]}]
                 (future/map (fn [_] (log/error logger "Message")) future-val)))))

(facts "When testing code"
  (components/mocked
    (a-function)

    (fact "uses mocked components only"
      (with-out-str
        (components/send! @queue {:payload "Some msg"}))
      => #"ERROR: Message\n\n\{:cid")))
