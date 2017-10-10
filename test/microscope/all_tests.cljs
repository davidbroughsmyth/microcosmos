(ns microscope.all-tests
  (:require [cljs.nodejs :as nodejs]
            [clojure.test :refer-macros [run-all-tests]]
            [microscope.future-test]
            [microscope.logging-test]
            [microscope.io-test]
            [microscope.env-test]
            [microscope.healthcheck-test]
            [microscope.core-test]))

(nodejs/enable-util-print!)

(def process (js/require "process"))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (println "All Tests Passed!")
    (do
      (println "Some tests failed")
      (aset process "exitCode" 1))))

(run-all-tests)
