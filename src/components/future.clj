(ns components.future
  (:refer-clojure :exclude [map])
  (:require [finagle-clojure.futures :as fut-finagle]
            [finagle-clojure.future-pool :as fut-pool]
            [clojure.core :as core])
  (:import  [java.util.concurrent Executors]))

(def just fut-finagle/value)
(def join fut-finagle/collect)

(def ^:private cpus (.availableProcessors (Runtime/getRuntime)))
(defonce pool (fut-pool/future-pool
                (Executors/newFixedThreadPool (* cpus 2))))

(defn map [fun & objs]
  (fut-finagle/map* (fut-finagle/collect objs) #(apply fun %)))

(defn flat-map [fun & objs]
  (fut-finagle/flatmap* (fut-finagle/collect objs) #(apply fun %)))

(defn on-success [fun & objs]
  (cond->> #(do (apply fun %) nil)
           :always (fut-finagle/on-success* (fut-finagle/collect objs))
           (= (count objs) 1) (map first)))

(defn on-failure [fun & objs]
  (fut-finagle/on-failure* (fut-finagle/collect objs) #(do
                                                         (fun %)
                                                         nil)))
(defn on-finish [fun & objs]
  (fut-finagle/ensure* (fut-finagle/collect objs) #(do
                                                     (fun)
                                                     nil)))

(defmacro execute [ & args]
  `(fut-pool/run* pool ~(cons `fn (cons [] args))))

(defn map-fork [fun & funs]
  (let [future (last funs)
        funs (butlast funs)]
    (->> funs
         (core/map (fn [fun] (flat-map #(execute (fun %)) future)))
         (cons (flat-map #(execute (fun %)) future))
         vec)))
