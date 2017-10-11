(ns microscope.rabbit.node-js
  (:require [microscope.io :as io]
            [microscope.healthcheck :as health]
            [microscope.core :as components]
            [microscope.future :as future]
            [microscope.logging :as log]
            [clojure.walk :as walk]
            [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)

(defn- clj-map [map] (-> map js->clj walk/keywordize-keys))
(defn- flatten-map [map] (-> map clj-map seq flatten))

(defn- decorate-component [component]
  (let [js-component (clj->js component)]
    (when (satisfies? io/IO component)
      (aset js-component "send" (fn [msg] (io/send! component (clj-map msg)))))

    (when (satisfies? log/Log component)
      (aset js-component "info"
            (fn [msg data] (apply log/info component msg (flatten-map data))))
      (aset js-component "warning"
            (fn [msg data] (apply log/warning component msg (flatten-map data))))
      (aset js-component "error"
            (fn [msg data] (apply log/error component msg (flatten-map data))))
      (aset js-component "fatal"
            (fn [msg data] (apply log/fatal component msg (flatten-map data)))))

    js-component))

(defn subscribe-with [components]
  (let [subscribe (apply components/subscribe-with (flatten-map components))]
    (fn [component callback]
      (subscribe (keyword component)
                 (fn [f-msg components]
                   (callback (future/map clj->js f-msg)
                             (->> components
                                  (map (fn [[k v]] [k (decorate-component v)]))
                                  (into {})
                                  clj->js)))))))

(defn implement-io [obj]
  (reify io/IO
    (listen [_ function] (.listen obj function))
    (send! [_ message] (.send obj message))
    (ack! [_ message] (.ack obj message))
    (reject! [_ param ex] (.reject obj param ex))
    (log-message [_ log message] (.logMessage obj log message))))

(defn implement-logger [obj]
  (reify log/Log
    (log [_ message type data] (.log obj message (name type) (clj->js data)))))

(defn implement-health [obj]
  (reify health/Healthcheck
    (unhealthy? [_]
      (->> obj .isUnhealthy future/just (future/map js->clj)))))

(defn- js->clj->js [cljs-function]
  (fn [ & args]
    (->> args
         (map js->clj)
         (apply cljs-function)
         clj->js)))

(aset js/module "exports"
      #js {:implementIO implement-io
           :implementLogger implement-logger
           :implementHealthcheck implement-health
           :subscribeWith subscribe-with})
