(ns microscope.crypt-test
  (:require [midje.sweet :refer :all]
            [microscope.crypt :as crypt]))

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

(facts "about encryption"
  (let [{:keys [public private]} (crypt/gen-keys 2048)
        public-base64-key (crypt/to-base64 (.getEncoded public))
        private-base64-key (crypt/to-base64 (.getEncoded private))]

    (fact "transforms a structure to an encrypted one"
      (let [enc (crypt/encrypt [{:so-me "structure"}]
                               (crypt/public-key-from-base64 public-base64-key))]
        (crypt/asymmetric-dec enc private) => "[{\"so_me\":\"structure\"}]"))

    (fact "transforms an encrypted structure to a decrypted one"
      (let [enc (crypt/asymmetric-enc "[{\"so_me\":\"structure\"}]" public)]
        (crypt/decrypt enc (crypt/private-key-from-base64 private-base64-key))
        => [{:so-me "structure"}]))

    (fact "functions are complementary"
      (let [public-base64-key (crypt/to-base64 (.getEncoded public))
            private-base64-key (crypt/to-base64 (.getEncoded private))
            message [{:so-me "structure"}]]
        (crypt/decrypt (crypt/encrypt message
                                      (crypt/public-key-from-base64 public-base64-key))
                       (crypt/private-key-from-base64 private-base64-key))
        => message))))
