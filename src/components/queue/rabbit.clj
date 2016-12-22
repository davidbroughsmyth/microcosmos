(ns components.queue.rabbit
  (:require [cheshire.core :as json]
            [cheshire.generate :as generators]
            [clojure.core :as clj]
            [components.cid :as cid]
            [components.core :as components]
            [langohr.basic :as basic]
            [langohr.channel :as channel]
            [langohr.consumers :as consumers]
            [langohr.core :as core]
            [langohr.exchange :as exchange]
            [langohr.queue :as queue])
  (:import [com.rabbitmq.client LongString]))

(generators/add-encoder LongString generators/encode-str)

(defn- parse-payload [payload]
  (-> payload (String. "UTF-8") (json/decode true)))

(def rabbit-default-meta [:cluster-id :app-id :message-id :expiration :type :user-id
                          :delivery-tag :delivery-mode :priority :redelivery?
                          :routing-key :content-type :persistent? :reply-to
                          :content-encoding :correlation-id :exchange :timestamp])

(defn- parse-meta [meta]
  (let [normalize-kv (fn [[k v]] [(keyword k) (if (instance? LongString v)
                                                (str v)
                                                v)])
        headers (->> meta
                     :headers
                     (map normalize-kv)
                     (into {}))]
    (-> headers (merge meta) (dissoc :headers))))

(defn- normalize-headers [meta]
  (let [headers (->> meta
                     (map (fn [[k v]] [(clj/name k) v]))
                     (into {}))]
    (apply dissoc headers rabbit-default-meta)))

(defrecord Queue [channel name max-tries cid]
  components/IO
  (listen [_ function]
    (let [callback (fn [_ meta payload]
                     (function {:payload (parse-payload payload)
                                :meta (parse-meta meta)}))]
      (consumers/subscribe channel name callback)))

  (send! [_ {:keys [payload meta] :or {meta {}}}]
    (if cid
      (basic/publish channel "" name
                     (json/encode payload)
                     (assoc meta :headers (normalize-headers (assoc meta :cid cid))))
      (throw (IllegalArgumentException.
               (str "Can't publish to queue without CID. Maybe you tried to send a message "
                    "using `queue` from components' namespace. Prefer to use the"
                    "components' attribute to create one.")))))

  (ack! [_ {:keys [meta]}]
        (basic/ack channel (:delivery-tag meta)))

  (reject! [_ {:keys [meta]} ex]
    (basic/reject channel (:delivery-tag meta)))

  cid/CID
    (append-cid [rabbit cid]
      (->Queue channel name max-tries cid)))


(def connection (atom nil))
(def channel (atom nil))

(defn connect! []
  (reset! connection (core/connect))
  (reset! channel (channel/open @connection)))

(defn disconnect! []
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
    (->Queue @channel name (:max-tries opts) nil)))
