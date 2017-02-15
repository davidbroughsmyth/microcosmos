(defproject microscope/crypt "0.1.0"
  :description "Encrypt/Decrypt getting keys from env variables"
  :url "https://github.com/acessocard/microscope"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [microscope "0.1.0"]
                 [bouncycastle/bcprov-jdk16 "140"]]

  :profiles {:dev {:src-paths ["dev"]
                   :dependencies [[midje "1.8.3"]]
                   :plugins [[lein-midje "3.2.1"]]}})
