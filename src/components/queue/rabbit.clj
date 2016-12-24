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

(defn- parse-meta [meta]
  (let [normalize-kv (fn [[k v]] [(keyword k) (if (instance? LongString v)
                                                (str v)
                                                v)])
        headers (->> meta
                     :headers
                     (map normalize-kv)
                     (into {}))]
    (-> headers (merge meta) (dissoc :headers))))

(def rabbit-default-meta [:cluster-id :app-id :message-id :expiration :type :user-id
                          :delivery-tag :delivery-mode :priority :redelivery?
                          :routing-key :content-type :persistent? :reply-to
                          :content-encoding :correlation-id :exchange :timestamp])

(defn- normalize-headers [meta]
  (let [headers (->> meta
                     (map (fn [[k v]] [(clj/name k) v]))
                     (into {}))]
    (apply dissoc headers rabbit-default-meta)))

(defn- parse-payload [payload]
  (-> payload (String. "UTF-8") (json/decode true)))

(defn- callback-payload [callback max-retries self _ meta payload]
  (let [retries (or (get-in meta [:headers "retries"]) 0)
        ack-msg #(basic/ack (:channel self) (:delivery-tag meta))
        reject-msg #(basic/reject (:channel self) (:delivery-tag meta) false)
        requeue-msg #(basic/publish (:channel self) "" (:name self)
                                    payload
                                    (update meta :headers (fn [hash-map]
                                                            (doto hash-map
                                                              (.put "retries" (inc retries))))))]
    (cond
      (not (:redelivery? meta)) (callback {:payload (parse-payload payload)
                                           :meta (parse-meta meta)})
      (>= retries max-retries) (reject-msg)
      :requeue-message (do (ack-msg) (requeue-msg)))))

(defrecord Queue [channel name max-retries cid]
  components/IO
  (listen [self function]
    (let [callback (partial callback-payload function max-retries self)]
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

  (reject! [self msg ex]
    (let [meta (:meta msg)
          retries (-> meta :retries (or 0))
          tag (:delivery-tag meta)
          cid (:cid meta)]
      (if (>= retries max-retries)
        (basic/reject channel tag false)
        (do
          (basic/ack channel tag)
          (components/send! (cid/append-cid self cid)
                            (assoc-in msg [:meta :retries] (inc retries)))))))

  cid/CID
    (append-cid [rabbit cid] (->Queue channel name max-retries cid)))


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
                           :auto-ack false
                           :auto-delete false
                           :max-retries 5
                           :durable true
                           :ttl (* 24 60 60 1000)})

(defn queue [name & {:as opts}]
  (when-not @connection (connect!))
  (let [opts (merge default-queue-params opts)
        dead-letter-name (str name "-dlx")
        dead-letter-q-name (str name "-deadletter")]

    (queue/declare @channel name (-> opts
                                     (dissoc :max-retries :ttl)
                                     (assoc :arguments {"x-dead-letter-exchange" dead-letter-name
                                                        "x-message-ttl" (:ttl opts)})))
    (queue/declare @channel dead-letter-q-name
                   {:durable true :auto-delete false :exclusive false})

    (exchange/fanout @channel dead-letter-name {:durable true})
    (queue/bind @channel dead-letter-q-name dead-letter-name)
    (->Queue @channel name (:max-retries opts) nil)))
