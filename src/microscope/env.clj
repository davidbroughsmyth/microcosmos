(ns microscope.env
  (:require [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn secret-or-env
  ([key]
   (secret-or-env key "/run/secrets"))

  ([key secrets-path]
   (let [secret-file (io/file secrets-path (name key))]
     (if (.exists secret-file)
       (str/trim-newline (slurp secret-file))
       (env key)))))
