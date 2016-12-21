(ns components.queue.rabbit-test
  (:require [components.core :as components]
            [components.future :as future]
            [components.queue.rabbit :as rabbit]
            [cheshire.core :as json]
            [langohr.core :as core]
            [midje.sweet :refer :all]))

(defn- send-msg [fut-value {:keys [queue]}]
  (future/map (fn [value]
                (case value
                  "error" (throw (java.lang.InternalError. "Some Error"))
                  (components/send! (queue "test-result") value)))
              fut-value))

(defn result-for-message [msg]
  (let [sub (components/subscribe-with :queue #(rabbit/queue % :auto-delete true)
                                       :logger (components.logging/->DebugLogger))
        test-queue (rabbit/queue "test" :auto-delete true :max-retries 1)
        result-queue (rabbit/queue "test-result" :auto-delete true)
        p (promise)]
    (sub test-queue send-msg)
    (sub result-queue (fn [future _]
                        (future/map (fn [value]
                                      (println "RES" value)
                                      (deliver p value))
                                    future)))
    (components/send! test-queue msg)
    p))

(defn send-and-wait [msg]
  (deref (result-for-message msg) 1000 {:payload :timeout
                                        :meta :timeout}))

(facts "Handling messages on RabbitMQ's queue"
  (fact "handles message if successful"
    (:payload (send-and-wait {:payload {:some "msg"}})) => {:some "msg"})

  (fact "attaches metadata into msg"
    (:meta (send-and-wait {:payload {:some "msg"}, :meta {:a 10}}))
    => (contains {:a 10}))

  (fact "attaches CID between services"
    (get-in (send-and-wait {:payload "msg"}) [:meta :cid]) => "FOO.BAR"
    (provided
      (components/generate-cid nil) => "FOO"
      (components/generate-cid "FOO") => "FOO.BAR"))

  (against-background
    (before :facts (rabbit/connect!))
    (after :facts (rabbit/disconnect!))))
