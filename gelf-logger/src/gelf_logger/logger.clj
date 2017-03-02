(ns gelf-logger.logger
  (:require [gelfino.client :as gelf]
            [environ.core :refer [env]]
            [cheshire.core :as cheshire]
            [microscope.logging :as log]))

(defrecord Logger [host cid]
  log/Log

  (log [_ message type data]
    (let [json (-> data
                  (assoc :cid cid)
                  (assoc :message message)
                  (assoc :type (name type))
                  cheshire/encode)]
      (gelf/send-> host {:short_message json}))))

(defn logger [{:keys [cid mocked]}]
  (if mocked
    (log/->DebugLogger cid)
    (do
      (gelf/connect)
      (->Logger (or (env :gelf-host) "localhost") cid))))
