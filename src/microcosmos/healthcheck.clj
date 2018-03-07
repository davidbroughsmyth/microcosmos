(ns microcosmos.healthcheck
  (:require [cheshire.core :as json]
            [finagle-clojure.builder.server :as builder-server]
            [finagle-clojure.http.message :as msg]
            [finagle-clojure.http.server :as http-server]
            [finagle-clojure.service :as service]
            [microcosmos.future :as future]
            [microcosmos.io :as io]
            [microcosmos.logging :as log]))

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

(def ^:private health-promise-atom (atom nil))
(defn ^:private generate-default-health-checker [logger]
  (reify io/IO
    (listen [_ function]
            (let [server (http-server/serve ":8081" (service/mk [req]
                                                                (swap! health-promise-atom
                                                                       (constantly (promise)))
                                                                (function {})
                                                                @@health-promise-atom))]
              (reset! http-server server)))
    (send! [_ {:keys [payload meta]}]
           (deliver @health-promise-atom
                    (future/just (-> (msg/response (or (:status-code meta) 200))
                                     (msg/set-content-string (json/encode payload)))))
           (when-not (:result payload)
             (log/fatal (logger {:cid "HEALTHCHECK"}) "Healthcheck failed"
                        :additional-info (json/encode (:details payload)))))
    (ack! [_ _])
    (reject! [_ _ _]
             (deliver @health-promise-atom
                      (future/just (-> (msg/response 404)))))
    (log-message [_ _ _])))

(def default-health-checker (memoize generate-default-health-checker))

(defn health-checker-gen [{:keys [mocked logger-gen] :as f}]
  (if mocked
    (reify io/IO
      (listen [_ _])
      (send! [_ _])
      (ack! [_ _])
      (reject! [_ _ _]))
    (default-health-checker logger-gen)))
