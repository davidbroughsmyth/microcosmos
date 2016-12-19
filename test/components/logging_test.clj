(ns components.logging-test
  (:require [midje.sweet :refer :all]
            [components.logging :as log]
            [cheshire.core :as cheshire]))

(facts "when logging"
  (fact "logs to STDOUT in JSON format"
    (let [res (with-out-str (log/info log/default-logger "foo" :additional "data"))]
      (cheshire/decode res true) => {:type "info", :message "foo", :additional "data"}))

  (fact "logs exception"
    (let [res (with-out-str (try (throw (ex-info "example" {:foo "BAR"}))
                              (catch Exception e
                                (log/error log/default-logger "Error!" :ex e))))
          json-map (cheshire/decode res true)]
      json-map => (contains {:type "error" :message "Error!"})
      (:ex json-map) => (contains {:cause "example" :data {:foo "BAR"}}))))
