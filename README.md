# Microscope Project
[![Build Status](https://travis-ci.org/acessocard/microscope.svg?branch=master)](https://travis-ci.org/acessocard/microscope)
[![Clojars Project](https://img.shields.io/clojars/v/microscope.svg)](https://clojars.org/microscope)
[![Dependencies Status](https://jarkeeper.com/acessocard/microscope/status.svg)](https://jarkeeper.com/acessocard/microscope)

Microscope is a list of libraries to ease the use of Clojure with microservices.
It is _not_ an opinated framework, nor it's a complete solution like Rails is
for web, but most of the time it'll boostrap an application with relative ease of use.

This repository contains the source code of Microscope. By
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

## MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
