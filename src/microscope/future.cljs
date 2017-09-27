(ns microscope.future
  (:refer-clojure :exclude [map]))

(def join #(.all js/Promise %))
(defn just [value] (.resolve js/Promise value))

(defn execute* [f]
  (js/Promise. (fn [resolve] (resolve (f)))))

(defn map [fun & objs]
  (.then (join objs) #(apply fun %)))

(defn intercept [fun & objs]
  (case (count objs)
    0 (throw "Must have at least 1 future in list")
    1 (map #(do (fun %) %) (first objs))
    (apply map (fn [ & args] (apply fun args) args) objs)))

(def on-success #'intercept)

(defn on-failure [fun & objs]
  (.catch (join objs) fun))

(defn on-finish [fun & objs]
  (let [future (join objs)]
    (doto future
          (.then #(fun))
          (.catch #(fun)))))
