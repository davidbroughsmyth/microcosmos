(ns microcosmos.core-test
  (:refer-clojure :exclude [subs])
  (:require [finagle-clojure.future-pool :as fut-pool]
            [microcosmos.core :as components]
            [microcosmos.future :as future]
            [microcosmos.io :as io]
            [microcosmos.logging :as log]
            [midje.sweet :refer :all]))

(defn fake-component [other]
  (let [fn (atom nil)]
    (reify io/IO
      (send! [_ msg] (@fn msg))
      (listen [_ f] (reset! fn f))
      (ack! [_ msg] (io/send! other {:ack msg}))
      (reject! [_ msg ex] (io/send! other {:reject msg}))
      (log-message [_ logger msg] (log/info logger "Processing" :msg msg)))))

(def log-output (atom nil))
(defn logger [{:keys [cid]}]
  (reify
    log/Log
    (log [_ msg type data]
         (reset! log-output {:msg msg :type type :data (assoc data :cid cid)}))))

(defrecord SomeQueue [params msgs]
  io/IO
  (listen [_ function]
          (add-watch msgs :watch (fn [_ _ _ actual]
                                   (function actual))))
  (send! [_ _])
  (ack! [_ _])
  (reject! [_ _ _])
  (log-message [_ _ _]))

(facts "when subscribing for new messages"
  (with-redefs [future/pool (fut-pool/immediate-future-pool)]
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

      (fact "passes meta, logger, and CID from message to IO implementations"
        (let [msgs (atom nil)
              p (promise)
              queue-fn (fn [params] (->SomeQueue params msgs))
              subs (components/subscribe-with :queue queue-fn)]

          (subs :queue (fn [_ {:keys [queue]}] (future/just (deliver p (:params queue)))))
          (swap! msgs (constantly {:meta {:im-so "META!"
                                          :cid "FOO"}}))
          (deref p 1000 :TIMEOUT) => (just {:meta {:im-so "META!"}
                                            :cid #"FOO.*"
                                            :logger-gen log/default-logger-gen})))

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
        @log-output => (contains {:msg "Processing",
                                  :type :info,
                                  :data (contains {:msg "some-msg"})}))

      (fact "logs an error using logger and CID to correlate things"
        (subscribe :queue (fn [f _] (future/map #(Integer/parseInt %) f)))
        (io/send! component "ten")
        (:type @log-output) => :fatal
        (:data @log-output) => (contains {:cid string?
                                          :exception string?
                                          :backtrace string?})
        (-> @log-output :data :exception io/deserialize-msg)
        => {:cause "For input string: \"ten\""
            :via [{:type "java.lang.NumberFormatException"
                   :message "For input string: \"ten\""
                   :at ["java.lang.NumberFormatException" "forInputString"
                        "NumberFormatException.java" 65]}]}

        (-> @log-output :data :backtrace)
        => #"java\.lang\.NumberFormatException"))))

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
                (reject! [component param ex])
                (log-message [_ _ _]))))))

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
       (io/send! @queue {:payload "Some msg"})) => #"ERROR: Message\n\nCID:"))

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
