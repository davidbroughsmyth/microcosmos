(ns components.queue.rabbit-test
  (:require [components.core :as components]
            [components.cid :as cid]
            [components.future :as future]
            [components.queue.rabbit :as rabbit]
            [cheshire.core :as json]
            [langohr.core :as core]
            [midje.sweet :refer :all]))

(defn- send-msg [fut-value {:keys [result-q]}]
  (future/map (fn [value]
                (case value
                  "error" (throw (java.lang.InternalError. "Some Error"))
                  (components/send! result-q value)))
              fut-value))

(defn result-for-message [msg]
  (let [sub (components/subscribe-with :result-q (rabbit/queue "test-result" :auto-delete true)
                                       :logger (components.logging/->DebugLogger))
        test-queue (rabbit/queue "test" :auto-delete true :max-retries 1)
        result-queue (rabbit/queue "test-result" :auto-delete true)
        p (promise)]
    (sub test-queue send-msg)
    (sub result-queue (fn [future _]
                        (future/map (fn [value]
                                      (deliver p value))
                                    future)))
    (components/send! (cid/append-cid test-queue "FOO") msg)
    p))

(defn send-and-wait [msg]
  (deref (result-for-message msg) 1000 {:payload :timeout
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
