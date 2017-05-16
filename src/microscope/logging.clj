(ns microscope.logging
  (:require [cheshire.core :as cheshire]
            [cheshire.generate :as generators]
            [clojure.repl :refer [demunge]]
            [clojure.string :as str]))

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
        cheshire/encode
        println)))

(defn- stack->vector [ste]
  [(demunge (.getClassName ste))
   (.getMethodName ste)
   (.getFileName ste)
   (.getLineNumber ste)])

(defn- just-stack [stack]
  (let [stack (mapv #(update % 0 str/replace #"(eval|fn)\-*\d+" "$1") stack)
        class-size (-> (apply max-key (comp count first) stack) first count)
        method-size (-> (apply max-key (comp count second) stack) second count)]
    (mapv (fn [[class method file line]]
            (format (str "%" class-size "s" "  %-" method-size "s  %s:%d")
                    class method file line))
          stack)))

(defn- print-kv [data]
  (doseq [[k v] data]
    (if (instance? Exception v)
      (let [ex (Throwable->map v)
            stack (mapv stack->vector (:trace ex))]
        (println (str (str/upper-case (name k)) ":") (:cause ex))
        (doseq [m (:via ex)] (print-kv m))
        (println "TRACE: ")
        (println "" (str/join "  \n" (just-stack stack))))

      (println (str (str/upper-case (if (keyword? k) (name k) k)) ":") v))))

(defrecord DebugLogger [cid]
  Log
  (log [_ message type data]
    (when-not (= type :info)
      (println (str (-> type name .toUpperCase) ": " message "\n"))
      (println "CID:" cid)
      (print-kv data))))

(generators/add-encoder java.lang.Class generators/encode-str)
(generators/add-encoder java.lang.StackTraceElement
                        (fn [ste writer]
                          (generators/encode-str (clojure.string/join " " (stack->vector ste)) writer)))

(generators/add-encoder java.lang.Exception
  (fn [ex writer]
    (let [ex (Throwable->map ex)
      normalized (update ex :trace #(->> % (str/join "\n") demunge))]
      (generators/encode-map normalized writer))))


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
