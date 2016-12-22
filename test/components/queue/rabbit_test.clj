(ns components.queue.rabbit-test
  (:require [components.core :as components]
            [components.cid :as cid]
            [components.future :as future]
            [components.queue.rabbit :as rabbit]
            [cheshire.core :as json]
            [langohr.core :as core]
            [midje.sweet :refer :all]))

(def all-msgs (atom []))
(def all-processed (atom []))
(def all-deadletters (atom []))

(defn- send-msg [fut-value {:keys [result-q]}]
  (future/map (fn [value]
                (println "VAL: " value)
                (swap! all-msgs conj value)
                (case (:payload value)
                  "error" (throw (Exception. "Some Error"))
                  (components/send! result-q value)))
              fut-value))

(def sub (components/subscribe-with :result-q (rabbit/queue "test-result" :auto-delete true)
                                    :logger (components.logging/->DebugLogger)))

(defn in-future [f]
  (fn [future _]
    (future/map f future)))

(defn result-for-messages [ & msgs]
  (let [test-queue (rabbit/queue "test" :auto-delete true :max-retries 1)
        result-queue (rabbit/queue "test-result" :auto-delete true)
        deadletter-queue (rabbit/->Queue (:channel test-queue) "test-deadletter" 1000 "FOO")
        p (promise)]
    (sub test-queue send-msg)
    (sub result-queue (in-future #(do (swap! all-processed conj %) (deliver p %))))
    (sub deadletter-queue (in-future #(swap! all-deadletters conj %)))
    (doseq [msg msgs]
      (components/send! (cid/append-cid test-queue "FOO") msg))
    p))

(defn send-and-wait [msg]
  (deref (result-for-messages msg) 1000 {:payload :timeout
                                         :meta :timeout}))

(defn wait [promise]
  (deref promise 1000 {:payload :timeout
                       :meta :timeout}))

(facts "Handling messages on RabbitMQ's queue"
  (fact "handles message if successful"
    (:payload (send-and-wait {:payload {:some "msg"}})) => {:some "msg"})

  (fact "attaches metadata into msg"
    (:meta (send-and-wait {:payload {:some "msg"}, :meta {:a 10}}))
    => (contains {:a 10, :cid "FOO.BAR"}))

  (fact "attaches CID between services"
    (get-in (send-and-wait {:payload "msg"}) [:meta :cid]) => "FOO.BAR"
    (provided))

  (against-background
    (components/generate-cid "FOO") => "FOO.BAR"
    (components/generate-cid "FOO.BAR") => ..irrelevant..
    (before :facts (rabbit/connect!))
    (after :facts (rabbit/disconnect!))))

(fact "when message results in a failure"
  (fact "process message two times before generating a deadletter"
    (:payload (wait (result-for-messages {:payload "error"} {:payload "msg"}))) => "msg"
    (map :payload @all-processed) => ["msg"])

  (against-background
    (before :facts (do
                     (rabbit/connect!)
                     (reset! all-msgs [])
                     (reset! all-deadletters [])))
    (after :facts (rabbit/disconnect!))))
