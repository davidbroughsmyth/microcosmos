(ns microcosmos.logging-test
  (:require [clojure.test :refer-macros [deftest is testing run-tests async]]
            [microcosmos.logging :as log]
            [clojure.walk :as walk]))

(def default-logger (log/default-logger-gen {:cid "F"}))
(def mocked-logger (log/default-logger-gen {:mocked true :cid "FOO"}))

(defn decode-json [json-str]
  (->> json-str
       (.parse js/JSON)
       js->clj
       walk/keywordize-keys))

(deftest logging
  (testing "logs to STDOUT in JSON format"
    (let [res (with-out-str (log/info default-logger "foo" :additional "data"))]
      (is (= {:type "info"
              :message "foo"
              :additional "data"
              :cid "F"}
             (decode-json res))))))

(deftest exception-logging
  (testing "parses exception code"
    (let [ex (log/parse-exception (ex-info "example" {:foo "BAR"}))]
      (is (= "example" (:cause ex)))
      (is (vector? (:trace ex)))))

  (testing "debug logger prettifies exceptions"
    (let [res (with-out-str (log/fatal mocked-logger "Error!" :ex (ex-info "example" {:foo "BAR"})))]
      (is (re-find #"FATAL: Error!\n\nCID: FOO" res))
      (is (re-find #"EX: example" res))
      (is (re-find #"DATA: \{:foo BAR\}" res)))))
