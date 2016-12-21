(ns components.queue.rabbit
  (:require [cheshire.core :as json]
            [components.core :as components]
            [langohr.basic :as basic]
            [langohr.channel :as channel]
            [langohr.consumers :as consumers]
            [langohr.core :as core]
            [langohr.exchange :as exchange]
            [langohr.queue :as queue]))

(defrecord Queue [channel name max-tries]
  components/IO
  (listen [_ function]
    (consumers/subscribe channel name
                         (fn [_ meta payload]
                           (function {:payload (-> payload (String. "UTF-8") (json/decode true))
                                      :meta meta}))))

  (send! [_ {:keys [payload meta]}]
    (basic/publish channel "" name
                   (json/encode payload)
                   meta)))

  ; (ack! [_]))

  ; (listen [_ function]
  ;   (let [f (fn [_ meta payload] (function {:payload (-> payload
  ;                                                       (String. "UTF-8")
  ;                                                       edn/read-string)
  ;                                           :meta meta}))]
  ;     (consumers/subscribe channel name f {:auto-ack false}))))

(def connection (atom nil))
(def channel (atom nil))

(defn connect! []
  (println "CONNECTING")
  (reset! connection (core/connect))
  (reset! channel (channel/open @connection)))

(defn disconnect! []
  (println "DISCONNECTING")
  (when @connection
    (core/close @channel)
    (core/close @connection))
  (reset! channel nil)
  (reset! connection nil))

(def default-queue-params {:exclusive false
                           :auto-delete false
                           :max-tries 5
                           :durable true
                           :ttl (* 24 60 60 1000)})

(defn queue [name & {:as opts}]
  (when-not @connection (connect!))
  (let [opts (merge default-queue-params opts)
        dead-letter-name (str name "-dlx")
        dead-letter-q-name (str name "-deadleter")]

    (queue/declare @channel name (-> opts
                                     (dissoc :max-tries :ttl)
                                     (assoc :arguments {"x-dead-letter-exchange" dead-letter-name
                                                        "x-message-ttl" (:ttl opts)})))
    (queue/declare @channel dead-letter-q-name
                   {:durable true :auto-delete false :exclusive false})

    (exchange/fanout @channel dead-letter-name {:durable true})
    (queue/bind @channel dead-letter-q-name dead-letter-name)
    (->Queue @channel name (:max-tries opts))))
