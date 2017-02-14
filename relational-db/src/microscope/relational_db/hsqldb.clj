(ns microscope.relational-db.hsqldb
  (:require [clojure.java.jdbc :as jdbc]
            [microscope.relational-db :as db]))

(defn db [file-name]
  (-> "org.hsqldb.jdbc.JDBCDriver"
      (db/pool-for (str "jdbc:hsqldb:" file-name) "SA" "")
      db/->HSQLDB
      db/gen-constructor))
