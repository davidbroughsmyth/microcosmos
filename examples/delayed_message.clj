(ns delayed-message
  (:require [components.core :as components]
            [components.logging :as log]
            [components.future :as future]
            [components.queue.rabbit :as rabbit]))

(def sub (components/subscribe-with :result-q (rabbit/queue "test-result" :auto-delete true)
                                    :logger log/default-logger-gen))

(def queue (rabbit/queue "delayed-msgs" :delayed true))
(defn process-message [future {:keys [result-q]}]
  (->> future
      (future/map :payload)
      (future/map println)))

(sub queue process-message)

(comment
   (let [queue (queue {:cid  "FOOCID"})]
     (components/send! queue {:payload (str "Delayed for 5 seconds")
                              :meta {:x-delay 5000}})
     (components/send! queue {:payload (str "Delayed for 10 seconds")
                              :meta {:x-delay 10000}})
     (components/send! queue {:payload (str "Delayed for 2 seconds")
                              :meta {:x-delay 2000}})
     (components/send! queue {:payload (str "Delayed for 1 second")
                              :meta {:x-delay 1000}})
     (components/send! queue {:payload (str "Delayed for none")})))
