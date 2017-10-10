(ns microscope.healthcheck
  (:require [microscope.future :as future]
            [microscope.io :as io]))

(defprotocol Healthcheck
  (unhealthy? [component]
              "Checks if component is unhealthy. If returning a map, shows the
reasons why that component is marked as unhealthy"))

(defn- wrap-in-promise [[name component]]
  (let [unhealthy (future/just (unhealthy? component))]
    (-> unhealthy
        (.then #(vector name %))
        (.catch #(vector name (or % "failure found in healthcheck"))))))

(defn check [components-map]
  (let [health-map (->> components-map
                        (filter #(satisfies? Healthcheck (second %)))
                        (map wrap-in-promise)
                        future/join)]
    (. health-map then
      #(let [healthy? (->> % (some second) not)]
         {:result healthy? :details (into {} %)}))))

(defn handle-healthcheck [fut-request components]
  (let [healthchecker (:healthcheck components)
        fut-result (check components)]
    (future/map (fn [{:keys [meta]} {:keys [result] :as payload}]
                  (println meta payload result)
                  (io/send! healthchecker {:payload payload
                                           :meta {:status-code (if result 200 503)
                                                  :response (:response meta)}}))
                fut-request fut-result)))

(def http-server (atom nil))

(defn stop-health-checker! []
  (if-let [server @http-server]
    (.close server #(reset! http-server nil))))

(def http (js/require "http"))
(def default-health-checker
  (delay
     (reify io/IO
       (listen [_ function]
         (reset! http-server (. http createServer (fn [request response]
                                                    (function {:meta {:request request
                                                                      :response response}}))))
         (.listen @http-server 8081 "127.0.0.1" #(println "Healthchecker running...")))

       (send! [_ {:keys [payload meta]}]
         (aset (:response meta) "statusCode" (:status-code meta))
         (aset (:response meta) "body" (->> payload clj->js (.stringify js/JSON))))

       (ack! [_ {:keys [meta]}]
         (.end (:response meta) (.-body (:response meta))))

       (reject! [_ {:keys [meta]} _]
         (aset (:response meta) "statusCode" 404)
         (.end (:response meta) "\"REJECTED\""))

       (log-message [_ _ _]))))


;
; const hostname = '127.0.0.1';
; const port = 3000;
;
; const server = http.createServer((req, res) => {})
;   res.statusCode = 200;
;   res.setHeader('Content-Type', 'text/plain');
;   res.end('Hello World\n');
; ;
;
; server.listen(port, hostname, () => {})
;   console.log(`Server running at http://${hostname}:${port}/`);
; ;

(defn health-checker-gen [params]
  (if (:mocked params)
    (reify io/IO
      (listen [_ _])
      (send! [_ _])
      (ack! [_ _])
      (reject! [_ _ _]))
    @default-health-checker))
