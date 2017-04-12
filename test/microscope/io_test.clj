(ns microscope.io-test
  (:require [midje.sweet :refer :all]
            [microscope.io :as io]))

(fact "serializes JSON in a clojure way"
  (io/serialize-msg {:foo-one 10}) => "{\"foo_one\":10}"
  (io/serialize-msg {:foo/one 10}) => "{\"one\":10}")

(fact "de-serializes JSON in a clojure way"
  (io/deserialize-msg "{\"foo_one\":10}") => {:foo-one 10})
