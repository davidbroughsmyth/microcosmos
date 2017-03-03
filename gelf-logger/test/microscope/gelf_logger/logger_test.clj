(ns microscope.gelf-logger.logger-test
  (:require [midje.sweet :refer :all]
            [microscope.logging :as log]
            [cheshire.core :as json]
            [gelfino.client :as gelf.client]
            [microscope.gelf-logger.logger :as gelf]))

(facts "will generate a logger to GELF"
  (let [logger (gelf/logger {:cid "FOO"})]
    (log/info logger "some-msg" :a 10)) => ..ok..
  (provided
    (gelf.client/connect) => irrelevant
    (json/encode {:cid "FOO" :message "some-msg" :type "info" :a 10}) => ..json..
    (gelf.client/send-> "localhost" {:short_message ..json..}) => ..ok..))
