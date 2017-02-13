(ns microscope.future.test
  (:require [finagle-clojure.futures :as fut-finagle])
  (:import [com.twitter.util Awaitable]))

(defn- midje-error [ & errors]
  (with-meta
    {:notes errors}
    {:midje/data-laden-falsehood true}))

(defn- compare-futures [future value]
  (let [fut-value (fut-finagle/await future)]
    (if (= fut-value value)
      true
      (midje-error (str "Future's value was: " fut-value)
                   (str "Expected value was: " value)))))

(defn future= [value]
  (fn [future]
    (if (instance? Awaitable future)
      (compare-futures future value)
      (midje-error (str "Expected " value " to be a twitter's future")))))
