(ns user
  (:require [microscope.queue.rabbit :as rabbit]
            [microscope.healthcheck :as health]))

(defn stop-system []
  (rabbit/disconnect!)
  (health/stop-health-checker!))
