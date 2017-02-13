(ns components.crypt-test
  (:require [midje.sweet :refer :all]
            [components.crypt :as crypt]))

(facts "about rsa encryption"
  (fact "encrypts a single text"
    (let [key-pair (crypt/gen-keys 2048)
          encrypted (crypt/rsa-enc "foobar" (:public key-pair))]
      encrypted =not=> "foobar"
      (crypt/to-string (crypt/rsa-dec encrypted (:private key-pair))) => "foobar"))

  (fact "encrypts a long text"
    (let [key-pair (crypt/gen-keys 2048)
          algo "RSA/NONE/PKCS1Padding"
          txt (str (range 800))
          encrypted (crypt/rsa-enc txt (:public key-pair))]
      (crypt/to-string (crypt/rsa-dec encrypted (:private key-pair))) => txt)))

(facts "about AES encryption"
  (let [key (crypt/gen-aes-key)
        txt (str (range 800))]
    (fact "encrypts a long text"
      (crypt/aes-enc txt key) =not=> txt
      (crypt/to-string (crypt/aes-dec (crypt/aes-enc txt key) key)) => txt)))

(facts "about asymmetric encription with RSA and AES"
  (let [key (crypt/gen-keys 2048)
        txt (str (range 800))]
    (fact "encrypts a long text"
      (crypt/asymmetric-enc txt (:public key)) =not=> txt
      (crypt/asymmetric-dec (crypt/asymmetric-enc txt (:public key)) (:private key)) => txt)))
