(ns components.future
  (:refer-clojure :exclude [map])
  (:require [finagle-clojure.futures :as fut-finagle]
            [finagle-clojure.future-pool :as fut-pool])
  (:import  [java.util.concurrent Executors]))

(def just fut-finagle/value)

(def ^:private cpus (.availableProcessors (Runtime/getRuntime)))
(defonce pool (fut-pool/future-pool
                (Executors/newFixedThreadPool (+ 2 (* cpus 2)))))

(defn map [fun & objs]
  (fut-finagle/map* (fut-finagle/collect objs) #(apply fun %)))

(defn flat-map [fun & objs]
  (fut-finagle/flatmap* (fut-finagle/collect objs) #(apply fun %)))

(defn on-success [fun & objs]
  (fut-finagle/on-success* (fut-finagle/collect objs) #(do
                                                         (apply fun %)
                                                         nil)))

(defn on-failure [fun & objs]
  (fut-finagle/on-failure* (fut-finagle/collect objs) #(do
                                                         (fun %)
                                                         nil)))

(defmacro execute [ & args]
  `(fut-pool/run* pool ~(cons `fn (cons [] args))))
