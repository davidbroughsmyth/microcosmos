(ns microcosmos.all-tests
  (:require [cljs.nodejs :as nodejs]
            [clojure.test :refer-macros [run-all-tests]]
            [microcosmos.future-test]
            [microcosmos.logging-test]
            [microcosmos.io-test]
            [microcosmos.env-test]
            [microcosmos.healthcheck-test]
            [microcosmos.core-test]))

(nodejs/enable-util-print!)

(def process (js/require "process"))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (println "All Tests Passed!")
    (do
      (println "Some tests failed")
      (aset process "exitCode" 1))))

(run-all-tests)
