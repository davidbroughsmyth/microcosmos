(ns components.future-test
  (:require [midje.sweet :refer :all]
            [components.future.test :refer :all]
            [components.future :as future]))

(facts "when checking things that resolve on future"
  (fact "creates a future with a single argument"
    (future/just 10) => (future= 10))

  (fact "creates a future with a thread pool"
    (future/execute 10) => (future= 10)))

(facts "composing futures"
  (fact "await all futures with map"
    (let [fut (future/just 10)]
      (future/map inc fut) => (future= 11)
      (future/map + fut (future/just 7)) => (future= 17)))

  (fact "runs code, but don't changes result"
    (let [fut (future/just 10)
          result (promise)
          future-result (future/intercept #(deliver result %) fut)]
      @result => 10
      future-result => (future= 10)))

  (fact "runs code, but don't changes result, with multiple futures"
    (let [f1 (future/just 10)
          f2 (future/just 20)
          result (promise)
          future-result (future/intercept #(deliver result (+ %1 %2)) f1 f2)]
      @result => 30
      future-result => (future= [10 20])))

  (fact "flattens nested futures with flat-map"
    (let [fut (future/just 10)]
      (future/flat-map #(future/just (+ % 10)) fut) => (future= 20)))

  (fact "forks multiple operations, and joins then together"
    (let [fut (future/just 10)
          results (future/map-fork #(+ % 10) #(+ % 20) fut)]
      (future/join results) => (future= [20 30]))))

(defmacro with-promise [code]
  `(let [~'promise (promise)]
     ~code
     (deref ~'promise 100 :TIMEOUT)))

(facts "when listening to futures"
  (let [success (future/just 10)
        failure (future/execute (throw (ex-info {:some "error"})))]
    (fact "listens to success"
      (with-promise
        (future/on-success (fn [_] (deliver promise :success)) success)) => :success)

    (fact "listens to failure"
      (with-promise
        (future/on-failure (fn [_] (deliver promise :fail)) failure)) => :fail

      (with-promise
        (future/on-failure (fn [_] (deliver promise :fail)) success failure)) => :fail)

    (fact "listens to anything"
      (with-promise (future/on-finish (fn [] (deliver promise :all)) failure)) => :all)

    (fact "pass-through the old result code when listening"
      (let [r (future/on-success (fn [map] (assoc map :id 10)) (future/execute {:id 1}))]
        r => (future= {:id 1}))

      (let [v1 (future/execute 10)
            v2 (future/execute 20)
            inner-result (promise)]
        (future/on-success #(deliver inner-result (+ %1 %2)) v1 v2) => (future= [10 20])
        (deref inner-result 100 :TIMEOUT) => 30))))
