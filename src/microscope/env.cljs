(ns microscope.env
  (:require [clojure.string :as str]))

(def ^:private fs (js/require "fs"))
(def ^:private path (js/require "path"))

(defn- translate-key-to-env [key]
  (-> key name (str/replace #"-" "_") str/upper-case))
(defn secret-or-env
  ([key]
   (secret-or-env key "/run/secrets"))

  ([key secrets-path]
   (let [secret-file (. path join secrets-path (name key))]
     (if (. fs existsSync secret-file)
       (str/trim-newline (-> fs (.readFileSync secret-file) str))
       (aget (.-env js/process) (translate-key-to-env key))))))
      ;  (aget (. js/process .-env))))))
