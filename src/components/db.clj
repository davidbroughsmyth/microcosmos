(ns components.db
  (:require [components.db.sqlite :as sqlite]))

(defprotocol RelationalDatabase
  (execute! [db ddl-query])
  (query [db sql-query & params]))

(defn connect-to [ & {:as connection-params}]
  (fn [params]
    (if (:mocked params)
      (sqlite/adapter ":memory:")
      (case (:adapter connection-params)
        :sqlite (sqlite/adapter (:file connection-params))))))
