(ns microscope.io-test
  (:require [midje.sweet :refer :all]
            [microscope.io :as io]))

(facts "about serializing JSON"
  (fact "serializes in a clojure way"
    (io/serialize-msg {:foo-one 10}) => "{\"foo_one\":10}"
    (io/serialize-msg {:foo/one 10}) => "{\"one\":10}")

  (fact "demunges function names"
    (io/serialize-msg string?) => #"clojure.core/string\?")

  (fact "demunges atoms or references"
    (io/serialize-msg (atom "foo")) => #"foo")

  (fact "serializes exception info"
    (let [ex (try (throw (ex-info "FOO" {:foo integer? :bar (atom nil)})) (catch Throwable e e))]
      (io/serialize-msg ex)) => #"FOO"))

(fact "de-serializes JSON in a clojure way"
  (io/deserialize-msg "{\"foo_one\":10}") => {:foo-one 10})
