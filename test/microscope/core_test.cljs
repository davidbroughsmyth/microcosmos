(ns microscope.core-test
  (:refer-clojure :exclude [subs])
  (:require-macros [microscope.core :as c-macros]
                   [cljs.core.async.macros :refer [go]])
  (:require [clojure.test :refer-macros [deftest is testing run-tests async]]
            [microscope.core :as components]
            [microscope.logging :as log]
            [microscope.future :as future]
            [cljs.core.async :refer [chan >! <!]]
            [microscope.io :as io]))

(def queue (atom nil))
(defn fake-queue [{:keys [mocked]}]
  (when mocked
    (let [atom (atom nil)]
      (reset! queue
              (reify io/IO
                (listen [component function]
                  (add-watch atom :obs
                             (fn [_ _ _ value]
                               (function value))))
               (send! [component message]
                 (js/Promise.
                  (fn [resolve reject]
                    (reset! atom (assoc message :meta {:resolve resolve
                                                       :reject reject})))))
               (ack! [component msg]
                 (let [resolve (-> msg :meta :resolve)]
                   (resolve true)))
               (reject! [component msg ex])
               (log-message [_ _ _]))))))

(defn a-function []
  (let [subscribe (components/subscribe-with :logger log/default-logger-gen
                                             :fake-queue fake-queue)]
    (subscribe :fake-queue
               (fn [future-val {:keys [logger]}]
                 (future/map (fn [_] (log/error logger "Message")) future-val)))))

(def log-msgs (atom []))

(deftest testing-code
  (async done
    (reset! log-msgs [])
    (c-macros/mocked {:mocks {:logger (fn [_]
                                        (reify log/Log
                                          (log [_ message type data]
                                               (swap! log-msgs conj {:msg message
                                                                     :type type
                                                                     :data data}))))}}
      (a-function)
      (.then (io/send! @queue {:payload "Some msg"})
            #(do
               (is (= [{:msg "Message" :type :error :data nil}]
                      @log-msgs))
               (done))))))
