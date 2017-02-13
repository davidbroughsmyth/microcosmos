(ns microscope.core
  (:require [microscope.future :as future]
            [microscope.logging :as log]
            [microscope.io :as io]
            [finagle-clojure.future-pool :as fut-pool]
            [microscope.healthcheck :as health]))

(def listen io/listen)
(def send! io/send!)
(def ack! io/ack!)
(def reject! io/reject!)

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

(def ^:private get-generators identity)

(defn- handler-for-component [components-generators io-component callback data]
  (let [params (params-for-generators data)
        components (->> components-generators
                        get-generators
                        (map (fn [[k generator]] [k (generator params)]))
                        (into {}))
        logger (:logger components)
        ack-msg (fn [_] (ack! io-component data))
        reject-msg (fn [ex]
                     (log/fatal logger "Uncaught Exception" :ex ex)
                     (reject! io-component data ex))]
    (log/info logger "Processing message" :msg data)
    (->> (callback (future/execute data) components)
         (future/on-success ack-msg)
         (future/on-failure reject-msg))))

(defn subscribe-with
  "Defines a subscribe function for an IO component (like a Rabbit Queue, HTTP handler,
or something like that). Returns a function that can be used (and re-used) to subscribe
to components

Accepts key-value pairs to define additional microscope. A component generator function
is, normally, a function that accepts some parameters and returns another function
(the generator). Generators MUST accept a single parameter that'll configure
additional data, such as `:cid` and `:mocked`.

For example, to subscribe to a RabbitMQ's queue, one can use:

(let [subscribe (subscribe-with :result-q (queue \"result\"))
                                :data-q (queue \"data\")]
  (subscribe :data-q (fn [f-message components] .....)))

The callback function (that will be passed to subscribe) will be called with two
arguments: one is the message (it will be a `Future`, and when resolved will be a map
containing at least `:payload` and `:meta`) and other is a map containing all the
components previously defined (in the above case, it'll be a map with only `:result-q`
key).

When subscribing to events with this function, the message being processed will be
logged (automatically) and it'll be automatically ACKed or REJECTed in case of success
or failure"
  [ & {:as components-generators}]
  (let [components-generators (-> components-generators
                                  get-generators
                                  (update :logger #(or % log/default-logger-gen))
                                  (update :healthcheck #(or % health/health-checker-gen)))]
    (fn [comp-to-listen callback]
        (let [generator (get components-generators comp-to-listen)
              component (generator (params-for-generators {}))
              callback-fn (partial handler-for-component
                                   components-generators
                                   component
                                   callback)]
          (listen component callback-fn)))))

(defmacro mocked
  "Generates a mocked environment, for tests. In this mode, `db` is set to sqlite,
memory only, RabbitMQ is disabled and code is used to coordinate between messages, etc.

If the first argument is a Map, it'll be used to pass parameters to mocked environment.
One possible parameter is `:mocks` - a map where keys are defined components, and values
are the mocked microscope. Other parameters are dependend of each component implementation."
  [ & args]
  (let [possible-params (first args)
        params (cond-> {:mocked true}
                       (map? possible-params) (merge possible-params))
        mocked-comps (->> (get params :mocks {}))]
    `(let [function# ~params-for-generators]
       (with-redefs [params-for-generators #(merge (function# %) ~params)
                     get-generators #(merge % ~mocked-comps)
                     future/pool (fut-pool/immediate-future-pool)]
         ~(cons `do args)))))
