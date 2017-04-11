(ns microscope.logging-test
  (:require [midje.sweet :refer :all]
            [microscope.core :as components]
            [microscope.logging :as log]
            [cheshire.core :as cheshire]))

(def default-logger (log/default-logger-gen {}))
(def mocked-logger (log/default-logger-gen {:mocked true :cid "FOO"}))
(facts "when logging"
  (fact "logs to STDOUT in JSON format"
    (let [res (with-out-str (log/info default-logger "foo" :additional "data"))]
      (cheshire/decode res true) => {:type "info"
                                     :message "foo"
                                     :additional "data"
                                     :cid nil}))

  (fact "logs exception"
    (let [res (with-out-str (try (throw (ex-info "example" {:foo "BAR"}))
                              (catch Exception e
                                (log/error default-logger "Error!" :ex e))))
          json-map (cheshire/decode res true)]
      json-map => (contains {:type "error" :message "Error!"})
      (:ex json-map) => (contains {:cause "example" :data {:foo "BAR"}})))

  (fact "debug logger prettifies exceptions"
    (let [res (with-out-str (try (throw (ex-info "example" {:foo "BAR"}))
                              (catch Exception e
                                (log/fatal mocked-logger "Error!" :ex e))))]
      res => #(re-find #"FATAL: Error!\n\nCID: FOO" %)
      res => #(re-find #"EX: example" %)
      res => #(re-find #"DATA: \{:foo BAR\}" %))))
