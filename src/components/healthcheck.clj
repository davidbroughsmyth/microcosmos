(ns components.healthcheck)

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
