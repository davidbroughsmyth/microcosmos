(defproject components "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.novemberain/langohr "3.6.1"]
                 [cheshire "5.6.3"]
                 [finagle-clojure/core "0.7.0"]
                 [finagle-clojure/http "0.7.0"]
                 [environ "1.1.0"]
                 [bouncycastle/bcprov-jdk16 "140"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.xerial/sqlite-jdbc "3.15.1"]]

  :profiles {:dev {:src-paths ["dev"]
                   :dependencies [[midje "1.8.3"]]
                   :plugins [[lein-midje "3.2.1"]]}})
