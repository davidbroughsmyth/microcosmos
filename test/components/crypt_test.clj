(ns components.crypt-test
  (:require [midje.sweet :refer :all]
            [components.crypt :as crypt]))

(facts "about asymmetric encryption"
  (fact "encrypts a single text"
    (let [key-pair (crypt/gen-keys "RSA" 1024)
          encrypted (crypt/asymmetric-enc "foobar" (:public key-pair))]
      encrypted =not=> "foobar"
      (crypt/asymmetric-dec encrypted (:private key-pair)) => "foobar"))

  (fact "encrypts a long text"
    (let [key-pair (crypt/gen-keys "RSA" 1024)
          algo "RSA/NONE/PKCS1Padding"
          txt (str (range 800))
          encrypted (crypt/asymmetric-enc txt (:public key-pair))]
      (crypt/asymmetric-dec encrypted (:private key-pair)) => txt)))
