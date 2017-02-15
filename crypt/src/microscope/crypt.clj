(ns microscope.crypt
  (:require [microscope.io :as io])
  (:import [java.security Security KeyPairGenerator]
           [javax.crypto Cipher KeyGenerator]
           [javax.crypto.spec SecretKeySpec IvParameterSpec]
           [org.bouncycastle.jce.provider BouncyCastleProvider]
           [org.bouncycastle.crypto.encodings PKCS1Encoding]
           [org.bouncycastle.crypto.engines RSAEngine]
           [org.bouncycastle.crypto.util PrivateKeyFactory PublicKeyFactory]
           [org.apache.commons.codec.binary Base64]))

(Security/addProvider (BouncyCastleProvider.))

(defn- concat-byte-arrays [byte-arrays]
  (let [total-size (reduce + (map count byte-arrays))
        result     (byte-array total-size)
        bb         (java.nio.ByteBuffer/wrap result)]
    (doseq [ba byte-arrays] (.put bb ba))
    result))

(defn gen-keys [size]
  (let [generator (doto (KeyPairGenerator/getInstance "RSA" "BC")
                        (.initialize size))]
    (-> generator
        .generateKeyPair
        bean
        (dissoc :class))))

(defn gen-aes-key []
  (-> (doto (KeyGenerator/getInstance "AES") (.init 256))
      .generateKey
      .getEncoded))

(defn string->bytes [txt]
  (if (string? txt) (.getBytes txt "UTF-8") txt))

(defn base64->bytes [txt]
  (if (string? txt) (Base64/decodeBase64 txt) txt))

(defn to-base64 [txt]
  (if (string? txt) txt (Base64/encodeBase64String txt)))

(defn to-string [txt]
  (if (string? txt) txt (String. txt "UTF-8")))

(defn aes-enc [txt key]
  (let [cipher (doto (Cipher/getInstance "AES/CBC/PKCS5Padding" "BC")
                     (.init Cipher/ENCRYPT_MODE (SecretKeySpec. key "AES")))
        bytes (string->bytes txt)
        cipher-text (.doFinal cipher bytes)
        iv (.getIV cipher)]
    (concat-byte-arrays [iv cipher-text])))

(defn aes-dec [txt key]
  (let [bytes (base64->bytes txt)
        [iv cipher-text] (split-at 16 bytes)
        cipher (doto (Cipher/getInstance "AES/CBC/PKCS5Padding" "BC")
                     (.init Cipher/DECRYPT_MODE (SecretKeySpec. key "AES") (IvParameterSpec. (byte-array iv))))]
    (.doFinal cipher (byte-array cipher-text))))

(defn- encrypt-or-decrypt [bytes key encrypt?]
  (let [engine (doto (PKCS1Encoding. (RSAEngine.))
                     (.init encrypt? key))
        block-size (.getInputBlockSize engine)
        msg-size (count bytes)
        fun #(cond-> %1 (pos? %3) (conj (.processBlock engine bytes %2 %3)))]

    (loop [processed 0
           cipher-text []]
      (if (> (+ processed block-size) msg-size)
        (concat-byte-arrays (fun cipher-text processed (- msg-size processed)))
        (recur
          (+ processed block-size)
          (fun cipher-text processed block-size))))))

(defn rsa-enc [txt key]
  (let [bytes (string->bytes txt)
        public-key (PublicKeyFactory/createKey (.getEncoded key))]
    (encrypt-or-decrypt bytes public-key true)))

(defn rsa-dec [txt key]
  (let [bytes (base64->bytes txt)
        private-key (PrivateKeyFactory/createKey (.getEncoded key))]
    (encrypt-or-decrypt bytes private-key false)))

(defn asymmetric-enc [txt public-key]
  (let [key (gen-aes-key)]
    (to-base64 (concat-byte-arrays [(rsa-enc key public-key)
                                    (aes-enc txt key)]))))

(defn asymmetric-dec [txt private-key]
  (let [[encr-key cipher-text] (->> txt base64->bytes (split-at 256) (map byte-array))
        key (rsa-dec encr-key private-key)]
    (to-string (aes-dec cipher-text key))))

(defn public-key-from-base64 [base64-str]
  (let [key-bytes (base64->bytes base64-str)
        spec (java.security.spec.X509EncodedKeySpec. key-bytes)
        key-factory (java.security.KeyFactory/getInstance "RSA")]
    (.generatePublic key-factory spec)))

(defn private-key-from-base64 [base64-str]
  (let [key-bytes (base64->bytes base64-str)
        spec (java.security.spec.PKCS8EncodedKeySpec. key-bytes)
        key-factory (java.security.KeyFactory/getInstance "RSA")]
    (.generatePrivate key-factory spec)))

(defn encrypt [sexp pubkey]
  (-> sexp io/serialize-msg (asymmetric-enc pubkey)))

(defn decrypt [message privkey]
  (let [json (asymmetric-dec message privkey)]
    (io/deserialize-msg json)))

(defn for-encryption [public-base64-key]
  (fn [{:keys [mocked]}]
    (if mocked
      (fn [sexp] {:ENCRYPTED sexp})
      #(encrypt % (public-key-from-base64 public-base64-key)))))

(defn for-decryption [private-base64-key]
  (fn [{:keys [mocked]}]
    (if mocked
      #(or (:ENCRYPTED %) (throw (ex-info "Not Crypted!" {:msg %})))
      #(decrypt % (private-key-from-base64 private-base64-key)))))
