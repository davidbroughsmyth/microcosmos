(ns microscope.logging
  (:require [cheshire.core :as cheshire]
            [cheshire.generate :as generators]))

(defprotocol Log
  (log [_ message type data]
       "Logs 'message' in default 'type' level, with additional data 'data' (a Map)

Type can be :info, :warning, :error or :fatal"))

(defn decorate-logger [logger cid]
  (reify Log
    (log [_ message type data]
         (log logger message type (assoc data :cid cid)))))

(defrecord StdoutLogger [cid]
  Log
  (log [_ message type data]
    (-> data
        (assoc :cid cid)
        (assoc :message message)
        (assoc :type (name type))
        cheshire/encode
        println)))

(defrecord DebugLogger [cid]
  Log
  (log [_ message type data]
    (when-not (= type :info)
      (println (str (-> type name .toUpperCase)
                    ": " message "\n\n" (assoc data :cid cid))))))

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
