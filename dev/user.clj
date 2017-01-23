(ns user
  (:require [components.queue.rabbit :as rabbit]
            [components.healthcheck :as health]))

(defn stop-system []
  (rabbit/disconnect!)
  (health/stop-health-checker!))
