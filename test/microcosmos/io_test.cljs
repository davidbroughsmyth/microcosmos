(ns microcosmos.io-test
  (:require [clojure.test :refer-macros [deftest testing]]
            [check.core :refer-macros [check]]
            [microcosmos.io :as io]))

(deftest serializing-json
  (testing "serializing in a clojure way"
    (check (io/serialize-msg {:foo-one 10}) => "{\"foo_one\":10}")
    (check (io/serialize-msg {:foo/one 10}) => "{\"one\":10}"))

  (testing "demunges atoms or references"
    (check (io/serialize-msg (atom "foo")) => "\"#atom foo\"")))

  ; (testing "serializes exception info"
  ;   (let [ex (try (throw (ex-info "FOO" {:foo integer? :bar (atom nil)})) (catch js/Error e e))]
  ;     (is (= #"FAAAAO")
  ;         (io/serialize-msg ex)))))

(deftest deserializing-json
  (testing "de-serializes JSON in a clojure way"
    (check (io/deserialize-msg "{\"foo_one\":10}") => {:foo-one 10})))
