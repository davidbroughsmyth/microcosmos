(ns components.core
  (:require [components.future :as future]
            [components.logging :as log]))

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

(defn generate-cid [old-cid]
  (let [upcase-chars (map char (range (int \A) (inc (int \Z))))
        digits (range 10)
        alfa-digits (cycle (map str (concat upcase-chars digits)))
        cid-gen #(apply str (take % (random-sample 0.02 alfa-digits)))]
    (if old-cid
      (str old-cid "." (cid-gen 5))
      (cid-gen 8))))

(defn params-for-generators [msg-data]
  (let [cid (get-in msg-data [:meta :cid])
        new-cid (generate-cid cid)]
    {:cid new-cid}))

(defn- handler-for-component [components-generators io-component callback data]
  (let [params (params-for-generators data)
        components (->> components-generators
                        (map (fn [[k generator]] [k (generator params)]))
                        (into {}))
        logger (:logger components)
        ack-msg (fn [_] (ack! io-component data))
        reject-msg (fn [ex]
                     (log/fatal logger "Uncaught Exception" :ex ex)
                     (reject! io-component data ex))]
    (log/info logger "Processing message" :msg data)
    (->> (callback (future/just data) components)
         (future/on-success ack-msg)
         (future/on-failure reject-msg))))

(defn subscribe-with
  "Defines a subscribe function for an IO component (like a Rabbit Queue, HTTP handler,
or something like that). Returns a function that can be used (and re-used) to subscribe
to components

Accepts key-value pairs to define additional components. A component generator function
is, normally, a function that accepts some parameters and returns another function
(the generator). Generators MUST accept a single parameter that'll configure
additional data, such as `:cid` and `:mocked`.

For example, to subscribe to a RabbitMQ's queue, one can use:

(def subscribe (subscribe-with :result-q (queue \"result\")))
(subscribe (queue \"data\") (fn [f-message components] .....))

The callback function (that will be passed to subscribe) will be called with two
arguments: one is the message (it will be a `Future`, and when resolved will be a map
containing at least `:payload` and `:meta`) and other is a map containing all the
components previously defined (in the above case, it'll be a map with only `:result-q`
key).

When subscribing to events with this function, the message being processed will be
logged (automatically) and it'll be automatically ACKed or REJECTed in case of success
or failure"
  [ & {:as components-generators}]
  (let [components-generators (update components-generators
                                      :logger #(or % log/default-logger-gen))]
    (fn [io-gen callback]
      (let [component (io-gen (params-for-generators {}))]
        (listen component
                (partial handler-for-component components-generators component callback))))))

(defmacro mocked [ & args]
  `(let [function# ~params-for-generators]
     (with-redefs [params-for-generators #(assoc (function# %) :mocked true)]
       ~(cons `do args))))
