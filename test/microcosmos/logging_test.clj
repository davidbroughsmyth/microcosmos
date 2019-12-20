(ns microcosmos.logging-test
  (:require [midje.sweet :refer :all]
            [microcosmos.logging :as log]
            [cheshire.core :as cheshire]
            [matcher-combinators.midje :refer [match]]
            [matcher-combinators.matchers :as m]))

(def default-logger (log/default-logger-gen {:cid "F"}))
(def mocked-logger (log/default-logger-gen {:mocked true :cid "FOO"}))
(facts "when logging"
  (fact "logs to STDOUT in JSON format"
    (let [res (with-out-str (log/info default-logger "foo" :additional "data"))]
      (cheshire/decode res true) => {:type "info"
                                     :message "foo"
                                     :additional "data"
                                     :cid "F"}))

  (fact "parses exception code"
    (log/parse-exception (ex-info "example" {:foo "BAR"}))
    => (match {:cause "example"
               :via [{:type any?
                      :at vector?
                      :message "example"
                      :data {:foo "BAR"}}]
               :data {:foo "BAR"}
               :trace vector?}))

  (fact "logs exception"
    (let [res (with-out-str (log/error default-logger "Error!" :ex (ex-info "example" {:foo "BAR"})))
          json-map (cheshire/decode res true)]
      json-map => (just {:message "Error!"
                         :cid "F"
                         :type "error"
                         :ex string?})
      (:ex json-map) => #"\{.*\"cause\":\"example\""))

  (fact "debug logger prettifies exceptions"
    (let [res (with-out-str (log/fatal mocked-logger "Error!" :ex (ex-info "example" {:foo "BAR"})))]
      res => #(re-find #"FATAL: Error!\n\nCID: FOO" %)
      res => #(re-find #"EX: example" %)
      res => #(re-find #"DATA: \{:foo BAR\}" %))))
