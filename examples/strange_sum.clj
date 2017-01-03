(ns strange-sum
  (:require [components.core :as components]
            [components.logging :as log]
            [components.future :as future]
            [components.queue.rabbit :as rabbit]))

(def sub (components/subscribe-with :result-q (rabbit/queue "test-result" :auto-delete true)
                                    :logger log/default-logger-gen))

(defn normalize-payload [message]
  (let [payload (:payload message)
        n1 (:n1 payload)
        n2 (:n2 payload)]
    [n1 n2]))

(defn sum [[n1 n2]]
  (if (= n1 0)
    n2
    (recur [(dec n1) (inc n2)])))

(defn publish-result [result result-q]
  (components/send! result-q {:payload result}))

(defn process-sum [future {:keys [result-q]}]
  (->> future
      (future/map normalize-payload)
      (future/flat-map #(future/execute (sum %)))
      (future/map #(publish-result % result-q))))

(sub (rabbit/queue "sum") process-sum)

#_(
   (doseq [_ (range 100)]
     (components/send! ((rabbit/queue "sum") {:cid  "FOOCID"})
                       {:payload {:n1 10001000 :n2 20}})))

(sub (rabbit/queue "test-result" :auto-delete true)
     (fn [f _] (future/map #(println "RES:" (:payload %)) f)))
