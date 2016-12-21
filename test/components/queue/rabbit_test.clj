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
                  (components/send! (queue "test-result") {:payload "foo"})))
              fut-value))

(defn result-for-message [msg]
  (let [sub (components/subscribe-with :queue #(rabbit/queue % :auto-delete true))
        test-queue (rabbit/queue "test" :auto-delete true :max-retries 1)
        result-queue (rabbit/queue "test-result" :auto-delete true)
        p (promise)]
    (sub test-queue send-msg)
    (sub result-queue (fn [future _]
                        (future/map (fn [value]
                                      (deliver p value)))))
    (components/send! test-queue {:payload msg})
    p))

(defn as-str [promise]
  (json/parse-string (deref promise 1000 "null") true))

(background
   (before :facts (rabbit/connect!))
   (after :facts (rabbit/disconnect!)))

(facts "Handling messages on RabbitMQ's queue"
   (fact "handles message if successful"
     (as-str (result-for-message {:some "msg"}))) => {:some "msg"})
