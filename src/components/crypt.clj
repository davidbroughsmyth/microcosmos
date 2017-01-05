(ns components.crypt
  (:import [java.security Security KeyPairGenerator]
           [javax.crypto Cipher]
           [org.bouncycastle.jce.provider BouncyCastleProvider]
           [org.bouncycastle.crypto.encodings PKCS1Encoding]
           [org.bouncycastle.crypto.engines RSAEngine]
           [org.bouncycastle.crypto.util PrivateKeyFactory PublicKeyFactory]
           [org.apache.commons.codec.binary Base64]))

(Security/addProvider (BouncyCastleProvider.))

(defn gen-keys [algorithm size]
  (let [generator (doto (KeyPairGenerator/getInstance algorithm "BC")
                        (.initialize size))]
    (-> generator
        .generateKeyPair
        bean
        (dissoc :class))))

(defn slurp-key [f]
  (-> f
      java.io.FileReader.
      org.bouncycastle.openssl.PEMReader.
      .readObject))

(defn spit-key [f keys]
  (let [writer (org.bouncycastle.openssl.PEMWriter. (java.io.FileWriter. f))]
    (.writeObject writer keys)
    (.flush writer)))

(defn- encrypt-or-decrypt [bytes algorithm key mode]
  (let [cipher (doto (Cipher/getInstance algorithm "BC")
                     (.init mode key))]
        ; block-size (.getBlockSize cipher)]
    ; (doseq [slice (partition-all block-size block-size bytes)]
    ;   (println "STEP" (.getBlockSize cipher))
    ;   (.update cipher (byte-array slice)))
    (def c cipher)
    (def b bytes)
    (.doFinal cipher bytes)))

(def k (gen-keys "RSA" 2048))

(def text (str (range 100000)))
(def bytess (.getBytes text "UTF-8"))

(defn- concat-byte-arrays [byte-arrays]
  (let [total-size (reduce + (map count byte-arrays))
        result     (byte-array total-size)
        bb         (java.nio.ByteBuffer/wrap result)]
    (doseq [ba byte-arrays] (.put bb ba))
    result))

(defn asymmetric-enc [txt key]
  (let [bytes (.getBytes txt "UTF-8")
        public-key (PublicKeyFactory/createKey (.getEncoded key))
        engine (doto (PKCS1Encoding. (RSAEngine.))
                     (.init true public-key))
        block-size (.getInputBlockSize engine)
        msg-size (count bytes)
        enc #(conj %1 (.processBlock engine bytes %2 %3))]

    (loop [processed 0
           cipher-text []]
      (if (> (+ processed block-size) msg-size)
        (Base64/encodeBase64String
          (concat-byte-arrays (enc cipher-text processed (- msg-size processed))))
        (recur
          (+ processed block-size)
          (enc cipher-text processed block-size))))))

(defn asymmetric-dec [txt key]
  (let [bytes (Base64/decodeBase64 txt)
        private-key (PrivateKeyFactory/createKey (.getEncoded key))
        engine (doto (PKCS1Encoding. (RSAEngine.))
                     (.init false private-key))
        block-size (.getInputBlockSize engine)
        msg-size (count bytes)
        dec #(cond-> %1 (pos? %3) (conj (.processBlock engine bytes %2 %3)))]

    (loop [processed 0
           cipher-text []]
      (if (> (+ processed block-size) msg-size)
        (String. (concat-byte-arrays
                   (dec cipher-text processed (- msg-size processed)))
                 "UTF-8")
        (recur
          (+ processed block-size)
          (dec cipher-text processed block-size))))))
