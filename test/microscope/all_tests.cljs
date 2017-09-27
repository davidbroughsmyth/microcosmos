(ns microscope.all-tests
  (:require [cljs.nodejs :as nodejs]
            [microscope.future-test]
            [microscope.io-test]))

(nodejs/enable-util-print!)
