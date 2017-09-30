(ns microscope.all-tests
  (:require [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)

(require 'microscope.future-test)
(require 'microscope.logging-test)
(require 'microscope.io-test)
(require 'microscope.env-test)
(require 'microscope.healthcheck-test)
