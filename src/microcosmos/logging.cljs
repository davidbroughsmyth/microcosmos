(ns microcosmos.logging
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

(defprotocol Log
  (log [_ message type data]
       "Logs 'message' in default 'type' level, with additional data 'data' (a Map)

Type can be :info, :warning, :error or :fatal"))

(defrecord StdoutLogger [cid]
  Log
  (log [_ message type data]
    (-> data
        (assoc :cid cid)
        (assoc :message message)
        (assoc :type (name type))
        clj->js
        (->> (.stringify js/JSON))
        println)))

(defn parse-exception [ex]
  {:cause (.-message ex)
   :trace (str/split-lines (.-stack ex))})

(defn- print-kv [data]
  (doseq [[k v] data]
    (if (instance? js/Error v)
      (do
        (println (str (str/upper-case (name k)) ":") (.-message v))
        (when-let [d (.-data v)] (println "DATA:" d))
        (println "TRACE: ")
        (println (.-stack v)))
      (println (str (str/upper-case (if (keyword? k) (name k) k)) ":") v))))

(defrecord DebugLogger [cid]
  Log
  (log [_ message type data]
    (when-not (= type :info)
      (println (str (-> type name .toUpperCase) ": " message "\n"))
      (println "CID:" cid)
      (print-kv data))))

(defn default-logger-gen [{:keys [cid mocked]}]
  (if mocked
    (->DebugLogger cid)
    (->StdoutLogger cid)))

(defn info [logger message & {:as data}]
  (log logger message :info data))

(defn warning [logger message & {:as data}]
  (log logger message :warning data))

(defn error [logger message & {:as data}]
  (log logger message :error data))

(defn fatal [logger message & {:as data}]
  (log logger message :fatal data))
