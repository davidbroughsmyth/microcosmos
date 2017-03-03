(defproject microscope/gelf-logger "0.1.0"
  :description "A simple logger for GELF format"
  :url "http://github.com/acessocard/microscope"
  :license {:name "MIT License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [microscope "0.1.0"]
                 [com.narkisr/gelfino-client "0.8.1"]]

  :profiles {:dev {:dependencies [[midje "1.8.3"]]
                   :plugins [[lein-midje "3.2.1"]]}})
