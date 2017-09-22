(ns microscope.healthcheck
  (:require [cheshire.core :as json]
            [finagle-clojure.builder.server :as builder-server]
            [finagle-clojure.http.message :as msg]
            [finagle-clojure.http.server :as http-server]
            [finagle-clojure.service :as service]
            [microscope.future :as future]
            [microscope.io :as io]))

(defprotocol Healthcheck
  (unhealthy? [component]
              "Checks if component is unhealthy. If returning a map, shows the
reasons why that component is marked as unhealthy"))

(defn check [components-map]
  (let [health-map (->> components-map
                       (filter #(satisfies? Healthcheck (second %)))
                       (map (fn [[name component]] [name (unhealthy? component)])))
        healthy? (->> health-map (some second) not)]
    {:result healthy? :details (into {} health-map)}))

; FIXME: This is TEMPORARY!
; Until we have a REAL HTTP server component, we'll be using
; Finagle's component to at least serve healthchecks in a specific port
(defn handle-healthcheck [fut-request components]
  (let [healthcheck (:healthcheck components)
        result (check components)
        msg (cond-> {:payload result}
                    (not (:result result)) (assoc :meta {:status-code 503}))
        send! (constantly (io/send! healthcheck msg))]
    (future/map send! fut-request)))

(def http-server (atom nil))

(defn stop-health-checker! []
  (if-let [server @http-server]
    (builder-server/close! server)
    (reset! http-server nil)))

(def default-health-checker
  (delay
   (let [p (atom nil)]
     (reify io/IO
       (listen [_ function]
         (let [server (http-server/serve ":8081" (service/mk [_]
                                                   (reset! p (promise))
                                                   (function {})
                                                   @@p))]
           (reset! http-server server)))
       (send! [_ {:keys [payload meta]}]
         (deliver @p (-> (:status-code meta)
                         (or 200)
                         (msg/response)
                         (msg/set-content-string (json/encode payload))
                         future/just)))
       (ack! [_ _])
       (reject! [_ _ _]
          (deliver @p (future/just (msg/response 404))))
       (log-message [_ _ _])))))

(defn health-checker-gen [params]
  (if (:mocked params)
    (reify io/IO
      (listen [_ _])
      (send! [_ _])
      (ack! [_ _])
      (reject! [_ _ _]))
    @default-health-checker))
