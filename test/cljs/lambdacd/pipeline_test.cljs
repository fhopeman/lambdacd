(ns lambdacd.pipeline-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [dommy.core :as dommy]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.pipeline :as pipeline]
            [lambdacd.dom-utils :as dom]
            [lambdacd.testutils :as tu]))

(def some-build-step
  {:name "some-step"
   :type "step"
   :step-id [1 2 3]
   :children []
   :result {:status "success"
            :out "hello world"}})

(defn with-name [step name]
  (assoc step :name name))

(defn with-type [step name]
  (assoc step :type name))

(defn with-output [step output]
  (assoc step :result {:status "success" :out output}))

(defn with-children [step children]
  (assoc step :children children))

(def some-container-build-step
  (-> some-build-step
      (with-name "some-container")
      (with-type "container")
      (with-children [some-build-step])
      (with-output "hello from container")))

(def some-parallel-build-step
  (-> some-container-build-step
      (with-name "some-parallel-step")
      (with-type "parallel")
      (with-children [some-build-step])
      (with-output "hello from p")))

(defn steps [root]
  (sel root :li))

(defn step-label [step]
  (sel1 step :span))


(deftest pipeline-view-test
  (testing "rendering of a single build-step"
    (let [output-atom (atom "")]
      (tu/with-mounted-component
        (pipeline/build-step-component some-build-step output-atom 1)
        (fn [c div]
          (is (dom/found-in div #"some-step"))
          (is (dom/having-class "build-step" (step-label (first (steps div)))))
          (is (dom/having-data "status" "success" (first (steps div))))
          (is (= "hello world" (dom/after-click output-atom (step-label (first (steps div))))))))))
  (testing "rendering of a container build-step"
    (let [output-atom (atom "")]
      (tu/with-mounted-component
        (pipeline/build-step-component some-container-build-step output-atom 1)
        (fn [c div]
          (is (dom/found-in div #"some-container"))
          (is (dom/found-in (first (steps div)) #"some-step"))
          (is (dom/having-class "build-step" (step-label (first (steps div)))))
          (is (dom/having-data "status" "success" (first (steps div))))
          (is (dom/containing-ordered-list (first (steps div))))
          (is (= "hello from container" (dom/after-click output-atom (step-label (first (steps div))))))))))
  (testing "rendering of a parallel build-step"
    (let [output-atom (atom "")]
      (tu/with-mounted-component
        (pipeline/build-step-component some-parallel-build-step output-atom 1)
        (fn [c div]
          (is (dom/found-in div #"some-parallel-step"))
          (is (dom/found-in (first (steps div)) #"some-step"))
          (is (dom/having-class "build-step" (step-label (first (steps div)))))
          (is (dom/having-data "status" "success" (first (steps div))))
          (is (dom/containing-unordered-list (first (steps div))))
          (is (= "hello from p" (dom/after-click output-atom (step-label (first (steps div)))))))))))