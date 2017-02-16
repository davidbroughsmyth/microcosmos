# Microscope Crypt

A simple project to provide simple asymmetric encryption and decryption

## Usage

```clojure
(require '[microscope.core :as c]
         '[microscope.future :as future]
         '[microscope.crypt :as crypt]
         '[environ.core :refer [env]])

(let [subscribe (c/subscribe-with :encrypt (crypt/for-encryption (env :public-key))
                                  :decrypt (crypt/for-decryption (env :private-key))
                                  :some-queue (dont-matter-who))]
  (subscribe :some-queue (fn [f {:keys [encrypt decrypy]}]
                           (->> f
                                (future/map #(encrypt (:payload %)))
                                (future/intercept println) ; Will print encrypted
                                (future/map #(decrypt (:payload %)))
                                (future/intercept println))))) ; Will print decrypted
```
