(ns microcosmos.io-test
  (:require [clojure.test :refer-macros [deftest is testing run-tests async]]
            [microcosmos.io :as io]))

(deftest serializing-json
  (testing "serializing in a clojure way"
    (is (= "{\"foo_one\":10}"
           (io/serialize-msg {:foo-one 10})))

    (is (= "{\"one\":10}"
          (io/serialize-msg {:foo/one 10}))))

  (testing "demunges atoms or references"
    (is (= "\"#atom foo\""
           (io/serialize-msg (atom "foo"))))))

  ; (testing "serializes exception info"
  ;   (let [ex (try (throw (ex-info "FOO" {:foo integer? :bar (atom nil)})) (catch js/Error e e))]
  ;     (is (= #"FAAAAO")
  ;         (io/serialize-msg ex)))))

(deftest deserializing-json
  (testing "de-serializes JSON in a clojure way"
    (is (= {:foo-one 10}
           (io/deserialize-msg "{\"foo_one\":10}")))))
