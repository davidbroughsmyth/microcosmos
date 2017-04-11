(ns user
  (:require [microscope.healthcheck :as health]))

(defn stop-system []
  (health/stop-health-checker!))
