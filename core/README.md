# Microscope

General components to be reusable in every Clojure micro-service

## Usage

```clojure
(require '[microscope.core :as components]
         '[microscope.future :as future]
         '[microscope.queue.rabbit :as rabbit])

(def subscribe (subscribe-with :result (rabbit/queue "results")))

(defn increment [n] (inc n))

(defn publish-to [queue result]
  (components/send! queue {:payload result}))

(defn main- [ & args]
  (subscribe (rabbit/queue "numbers")
             (fn [future-n components]
               (->> future-n
                    (future/map :payload)
                    (future/map increment)
                    (future/map #(publish-to (:result components) %))))))
```

## Configuration

RabbitMQ's configuration is made entirely of two environment variables. First one
is named `RABBIT_CONFIG`, which will configure the hosts (with aliases) where Rabbit is
installed. Second is `RABBIT_QUEUES`, which will configure where each queue name is
located. So, let's say we have rabbit installed in two machines, one in `localhost`
and another in `192.168.0.30`. Then, we have three queues, one in `localhost`, and two
on this secondary machine. The environment variables' configuration will look like this:

```
RABBIT_CONFIG='{"local-machine":{"host":"localhost"},"remote":{"host":"192.168.0.30","port":1337,"username":"foobar","password":"SuperSecretPassword"}}'
RABBIT_QUEUES='{"numbers":"local-machine","results":"remote","other-result":"remote"}'
```

The complete list of parameters is located at: http://clojurerabbitmq.info/articles/connecting.html

## License

Copyright © 2016 AcessoCard
