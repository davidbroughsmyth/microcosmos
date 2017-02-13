(ns microscope.relational-db.sqlite
  (:require [clojure.java.jdbc :as jdbc]
            [microscope.relational-db :as db]))

(defn db [file-name]
  (db/gen-constructor
   (db/db-for "org.sqlite.JDBC"
              (str "jdbc:sqlite:" file-name)
              nil nil)))
