(ns user
  (:require [microcosmos.healthcheck :as health]))

(defn stop-system []
  (health/stop-health-checker!))
