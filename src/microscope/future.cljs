(ns microscope.future
  (:refer-clojure :exclude [map]))

(def join #(.all js/Promise %))
(defn just [value] (. js/Promise resolve value))

(defn execute* [f]
  (js/Promise. (fn [resolve] (resolve (f)))))

(defn map [fun & objs]
  (.then (join objs) #(apply fun %)))

(defn intercept [fun & objs]
  (case (count objs)
    0 (throw "Must have at least 1 future in list")
    1 (map #(do (fun %) %) (first objs))
    (apply map (fn [ & args] (apply fun args) args) objs)))

(defn on-success [fun & objs]
  (apply intercept (fn [ & args] (try
                                   (apply fun args)
                                   (catch :default e :foo)))
    objs))

(defn on-failure [fun & objs]
  (.catch (join objs) fun))

(defn on-finish [fun & objs]
  (->> objs
       join
       (on-success #(fun))
       (on-failure #(fun))))
