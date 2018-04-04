(ns microcosmos.io-test
  (:require [midje.sweet :refer :all]
            [microcosmos.io :as io]
            [clojure.test :refer :all]
            [check.core :refer :all]))

(deftest json-serialize
  (testing "serializes in a clojure way"
    (check (io/serialize-msg {:foo-one 10}) => "{\"foo_one\":10}")
    (check (io/serialize-msg {:foo/one 10}) => "{\"one\":10}"))

  (testing "demunges function names"
    (check (io/serialize-msg string?) => #"clojure.core/string\?"))

  (testing "demunges atoms or references"
    (check (io/serialize-msg (atom "foo")) => #"foo"))

  (testing "serializes exception info"
    (let [ex (try (throw (ex-info "FOO" {:foo integer? :bar (atom nil)}))
               (catch Throwable e e))]
      (check (io/serialize-msg ex) => #"FOO"))))

(deftest json-deserialize
  (testing "de-serializes JSON in a clojure way"
    (check (io/deserialize-msg "{\"foo_one\":10}") => {:foo-one 10})))
