(ns strange-sum
  (:require [microscope.core :as components]
            [microscope.logging :as log]
            [microscope.future :as future]
            [microscope.queue.rabbit :as rabbit]))

(defn logger [{:keys [cid]}]
  (log/->DebugLogger cid))

(def sub (components/subscribe-with :result-q (rabbit/queue "test-result" :auto-delete true)
                                    :sum (rabbit/queue "sum")
                                    :logger logger))

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
      (future/map sum)
      (future/map #(publish-result % result-q))))

(sub :sum process-sum)

(comment
   (doseq [_ (range 100)]
     (components/send! ((rabbit/queue "sum") {:cid  "FOOCID"})
                       {:payload {:n1 10001000 :n2 20}})))

(sub :result-q (fn [f _] (future/map #(println "RES:" (:payload %)) f)))
