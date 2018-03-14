(ns microcosmos.io
  #?(:clj
     (:require [clojure.main :as clj-main]
               [cheshire.generate :as generators]
               [cheshire.core :as json]
               [clojure.string :as str])
     :cljs
     (:require [clojure.string :as str]
               [clojure.walk :as walk])))

(defn- to-wire [key]
  (if (keyword? key)
    (-> key name (str/replace #"-" "_"))
    (str key)))

(defmulti serialize-type #(type %))
#?(:clj (do
          (defmethod serialize-type clojure.lang.IFn [f] (clj-main/demunge (str f)))
          (defmethod serialize-type clojure.lang.ARef [f] (with-out-str (prn f)))
          (defmethod serialize-type :default [obj] (str obj)))
   :cljs (do
          (defmethod serialize-type js/Function [f] "<function>")
          (defmethod serialize-type cljs.core/Atom [f] (str "#atom " @f))
          (defmethod serialize-type cljs.core/PersistentArrayMap [m]
            (->> m
                 (map (fn [[k v]]
                        (if (keyword? k)
                          [(to-wire k) (serialize-type v)]
                          [(serialize-type k) (serialize-type v)])))
                 (into {})))
          (defmethod serialize-type :default [obj] obj)))

#?(:clj
   (generators/add-encoder Object
                           (fn [obj writer]
                             (generators/encode-str (serialize-type obj) writer))))

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
this")
  (log-message [component log message]
               "Logs that we're consuming a message. This can be ignored by
some components (like Healthcheck, for example)."))

(defn serialize-msg [sexp]
  #?(:clj (json/encode sexp {:key-fn to-wire})
     :cljs (->> sexp
                (walk/postwalk serialize-type)
                clj->js
                (.stringify js/JSON))))

(defn- to-internal [key]
  (-> key str (str/replace #"_" "-") keyword))

(defn- sanitize [obj]
  (cond
    (map? obj) (->> obj
                   (map (fn [[k v]] [(to-internal k) (sanitize v)]))
                   (into {}))
    (vector? obj) (mapv sanitize obj)
    :else obj))

(defn deserialize-msg [json]
  #?(:clj (json/decode json to-internal)
     :cljs (->> json
                (.parse js/JSON)
                js->clj
                sanitize)))
