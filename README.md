# Microscope Project

This is the default page for Microscope project. Microscope is a list of libraries to ease
the use of Clojure with microservices. It is _not_ an opinated framework, nor it's a
complete solution like Rails is for web, but most of the time it'll boostrap an
application with relative ease of use.

This repository contains the source code of Microscope (located in `core` directory). By
itself, it does little to nothing - we need some kind of implementation for IO. There's
currently RabbitMQ implementation (it'll connect into a Rabbit queue, listen to it, and
send to other queues), and there's a planned feature to use HTTP too.

## Example code

Considering that we have a clojure app, and `project.clj` with `microscope` and
`microscope/rabbit` dependencies, we can create a microservice right away. But first,
let's test-drive a simple queue that listens to a queue named *numbers* and just sums all
then, publishing their results at *results*.

Test file (probably `core_test.clj`):

```clojure
(ns your-app.core-test
  (:require [clojure.test :refer :all]
            [your-app.core :as handler]
            [microscope.core :as components]
            [microscope.io :as io]
            [microscope.rabbit.queue :as rabbit]))

(deftest sum-service
  (testing "sums all services"
    (components/mocked
      (handler/-main)
      (io/send! (:numbers @rabbit/queues)
                {:payload {:numbers [10 20 30]}})
      (is (= 60
             (-> @rabbit/queues :results :messages deref last :payload))))))
```

This will fail - we didn't write any code. Then, it's time to implement the feature.

Source file (probably `core.clj`):

```clojure
(ns your-app.core
  (:require [microscope.core :as components]
            [microscope.io :as io]
            [microscope.rabbit.queue :as rabbit]
            [microscope.future :as future]))

(defn sum-everything [future-message components]
  (->> future-message
       (future/map :payload)
       (future/map :numbers)
       (future/map #(apply + %))
       (future/intercept #(io/send! (:results components)
                                    {:payload %}))))

(defn -main [ & args]
  (let [subscribe (components/subscribe-with :numbers (rabbit/queue "numbers")
                                             :results (rabbit/queue "results"))]
    (subscribe :numbers sum-everything)))
```

It's that simple.
