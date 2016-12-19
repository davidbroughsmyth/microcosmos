(ns components.logging
  (:require [cheshire.core :as cheshire]
            [cheshire.generate :as generators]))

(defprotocol Log
  (log [_ message type data]
       "Logs 'message' in default 'type' level, with additional data 'data' (a Map)

Type can be :info, :warning, :error or :fatal"))

(defn- log-to-stdout [msg data type])

(defrecord StdoutLogger []
  Log
  (log [_ message type data]
    (-> data
        (assoc :message message)
        (assoc :type (name type))
        cheshire/encode
        println)))

(generators/add-encoder java.lang.Class generators/encode-str)
(generators/add-encoder java.lang.StackTraceElement
                        (fn [ste writer]
                          (generators/encode-seq
                            [(.getClassName ste)
                             (.getMethodName ste)
                             (.getLineNumber ste)]
                            writer)))

(generators/add-encoder java.lang.Exception
                        (fn [ex writer]
                          (generators/encode-map (Throwable->map ex) writer)))

(def default-logger (->StdoutLogger))

(defn info [logger message & {:as data}]
  (log logger message :info data))

(defn warning [logger message & {:as data}]
  (log logger message :warning data))

(defn error [logger message & {:as data}]
  (log logger message :error data))

(defn fatal [logger message & {:as data}]
  (log logger message :fatal data))
