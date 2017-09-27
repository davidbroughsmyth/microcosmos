(ns microscope.future-test
  (:require [cljs.nodejs :as nodejs]
            [clojure.test :refer-macros [deftest is testing run-tests async]]
            [microscope.future :as future]))

(nodejs/enable-util-print!)

(deftest just-future
  (async done
    (.then (future/just 10) #(do
                               (is (= % 10))
                               (done)))))

(deftest execute
  (async done
    (.then (future/execute (do 10))
           #(do
              (is (= % 10))
              (done)))))

(deftest composing-futures-with-map
  (async done
    (testing "awaiting all futures with map"
      (let [fut (future/just 10)]
        (.then (future/map inc fut) #(is (= 11 %)))
        (.then (future/map + fut (future/just 7))
               #(do
                  (is (= 17 %))
                  (done)))))))

(deftest composing-futures-with-intercept
  (async done
    (testing "running code, but don't changing result"
      (let [fut (future/just 10)
            result (atom nil)
            future-result (future/intercept #(reset! result %) fut)]
        (.then future-result
               #(do
                  (is (= @result 10))
                  (is (= % 10))))))

    (testing "running code, but don't changes result, with multiple futures"
      (let [f1 (future/just 10)
            f2 (future/just 20)
            result (atom nil)
            future-result (future/intercept #(reset! result (+ %1 %2)) f1 f2)]
        (.then future-result
               #(do
                  (is (= @result 30))
                  (is (= % [10 20]))
                  (done)))))))


(deftest listen-to-future-results
  (async done
    (let [success (future/just 10)
          failure (future/execute (throw (ex-info "error" {:some "error"})))
          success-a (atom :something)
          failure-a (atom :something)]

      (future/on-success (fn [_] (reset! success-a :success)) success)
      (future/on-success (fn [_] (reset! failure-a :success)) failure)
      (future/on-failure (fn [_] (reset! success-a :fail)) success)
      (future/on-failure (fn [_] (reset! failure-a :fail)) failure)

      (future/on-finish (fn [ & args]
                            (is (= @success-a :success))
                            (is (= @failure-a :fail))
                            (done))
                        success failure))))

(run-tests)
