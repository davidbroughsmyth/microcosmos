(ns microscope.relational-db.hsqldb
  (:require [clojure.java.jdbc :as jdbc]
            [microscope.relational-db :as db]))

(defn db [file-name]
  (db/gen-constructor
   (db/db-for "org.hsqldb.jdbc.JDBCDriver"
              (str "jdbc:hsqldb:" file-name)
              "SA"
              "")))
