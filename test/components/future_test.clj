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

  (fact "flattens nested futures with flat-map"
    (let [fut (future/just 10)]
      (future/flat-map #(future/just (+ % 10)) fut) => (future= 20)))

  (fact "forks multiple operations, and joins then together"
    (let [fut (future/just 10)
          results (future/map-fork #(+ % 10) #(+ % 20) fut)]
      (future/join results) => (future= [20 30]))))

(facts "when listening to futures"
  (let [res (atom nil)
        success (future/just 10)
        failure (future/execute (throw (ex-info {:some "error"})))]
    (fact "listens to success"
      (future/on-success (fn [_] (reset! res :success)) success)
      @res => :success)

    (fact "listens to failure"
      (future/on-failure (fn [_] (reset! res :fail)) failure)
      @res => :fail

      (future/on-failure (fn [_] (reset! res :fail)) success failure)
      @res => :fail)

    (fact "listens to anything"
      (future/on-finish (fn [] (reset! res :all)) failure)
      @res => :all)

    (fact "pass-through the old result code when listening"
      (let [r (future/on-success (fn [map] (assoc map :id 10)) (future/execute {:id 1}))]
        r => (future= {:id 1}))

      (let [v1 (future/execute 10)
            v2 (future/execute 20)
            r (future/on-success #(reset! res (+ %1 %2)) v1 v2)]
        @res => 30
        r => (future= [10 20])))))
