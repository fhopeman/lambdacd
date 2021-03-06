(ns lambdacd.internal.pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [lambdacd.internal.pipeline-state-persistence :as persistence]
            [clj-time.core :as t]))

(def clean-pipeline-state {})

(defn initial-pipeline-state [{ home-dir :home-dir }]
  (persistence/read-build-history-from home-dir))

(defn- put-if-not-present [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))

(defn- update-current-run [step-id step-result current-state]
  (let [current-step-result (get current-state step-id)
        new-step-result (-> current-step-result
                            (assoc :most-recent-update-at (t/now))
                            (put-if-not-present :first-updated-at (t/now))
                            (merge step-result))]
    (assoc current-state step-id new-step-result)))

(defn- update-pipeline-state [build-number step-id step-result current-state]
  (assoc current-state build-number (update-current-run step-id step-result (get current-state build-number))))

(defn- most-recent-build-number-in-state [pipeline-state]
  (if-let [current-build-number (last (sort (keys pipeline-state)))]
    current-build-number
    0))

(defn next-build-number [{pipeline-state :_pipeline-state }]
  (inc (most-recent-build-number-in-state @pipeline-state)))

(defn- is-active? [step-result]
  (let [status (:status step-result)]
    (or (= status :running) (= status :waiting))))

(defn- active-first-steps [state]
  (let [first-steps (map #(get % [1]) (vals state))
        active-first-steps (filter is-active? first-steps)]
    active-first-steps))

(defn- call-callback-when-no-first-step-is-active [callback key reference old new]
  (let [active-first-steps-new (active-first-steps new)
        active-first-steps-old (active-first-steps old)]
    (if (and (empty? active-first-steps-new) (not (empty? active-first-steps-old)))
      (callback))))

(defn notify-when-no-first-step-is-active [{pipeline-state :_pipeline-state} callback]
  (add-watch pipeline-state :notify-most-recent-build-running (partial call-callback-when-no-first-step-is-active callback)))

(defn update [{step-id :step-id state :_pipeline-state build-number :build-number { home-dir :home-dir } :config } step-result]
  (if (not (nil? state)) ; convenience for tests: if no state exists we just do nothing
    (let [new-state (swap! state (partial update-pipeline-state build-number step-id step-result))]
      (persistence/write-build-history home-dir build-number new-state))))

(defn running [ctx]
  (update ctx {:status :running}))


