(ns microcosmos.components-test
  (:require [check.core :refer [check]]
            [clojure.test :refer :all]
            [microcosmos.components :as components]))

(defn micro-component [params]
  (if (:mocked params)
    (str "CID: " (-> params :message :meta :cid) " MOCKED")
    (str "CID: " (-> params :message :meta :cid))))

(def some-msg {:payload "foo" :meta {:cid "ASDF"}})

(def components {:some-c micro-component})

(deftest creating-components
  (testing "will create a microcosmos component"
    (check (components/create components :some-c some-msg)
           => "CID: ASDF"))

  (testing "will create a mocked microcosmos component"
    (check (components/mocked (components/create components :some-c some-msg))
           => "CID: ASDF MOCKED"))

  (testing "will create a mocked component with predef fn"
    (check (components/mocked {:mocks {:some-c "FOOBAR"}}
             (components/create components :some-c some-msg))
           => "FOOBAR")))
