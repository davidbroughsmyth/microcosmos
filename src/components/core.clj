(ns components.core
  (:require [components.future :as future]
            [components.logging :as log]
            [components.cid :as cid]))

(defprotocol IO
  (listen [component function]
          "Listens to new connections. Expects a function that will be called
with a single parameter - a Map containing (at least) :payload, which is the
message sent to this service")
  (send! [component message]
        "Sends a new message to this component. Different services can need
different keys, but at least must support :payload and :meta. For instance, RabbitMQ
needs :payload only but accepts :meta, HTTP probably can probably accept :payload, :meta,
and :header")
  (ack! [component param]
       "Sends an acknowledge that our last message was processed. Some services
may need this, some not. In queue services, informs the server that we processed
this last message. In some HTTP services, it can indicate (in case of streaming)
that the connection should be ended and we sent all data we had to send.
Some services can choose to ignore this.")
  (reject! [component param ex]
          "Rejects this message. Indicates to service thar some error has
occurred, and some action should be done. Some services can choose to ignore
this"))

(defn subscribe-with [ & {:as components}]
  (let [components (update components :logger #(or % log/default-logger))]
    (fn [component callback]
      (listen component (fn [data]
                          (let [cid (get-in data [:meta :cid])
                                new-cid (cid/generate-cid cid)
                                components (->> components
                                                (map (fn [[k v]] [k (cid/append-cid v new-cid)]))
                                                (into {}))]
                            (log/info (:logger components)
                                      "Processing message"
                                      :msg data)
                            (->> (callback (future/just data) components)
                                 (future/on-success (fn [_] (ack! component data)))
                                 (future/on-failure (fn [ex]
                                                      (log/fatal (:logger components)
                                                                 "Uncaught Exception"
                                                                 :ex ex)
                                                      (reject! component data ex))))))))))
