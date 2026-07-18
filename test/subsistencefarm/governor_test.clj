(ns subsistencefarm.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [subsistencefarm.store :as store]
            [subsistencefarm.advisor :as advisor]
            [subsistencefarm.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-farmer! st {:farmer-id "farmer-1" :name "Kobo Yamada"})
    (store/register-plot! st {:plot-id "P-1" :name "Kobo Household Plot" :max-supply-cost 300})
    st))

(defn- op [op-kw & {:as extra}]
  (merge {:op op-kw :effect :propose :plot-id "P-1"
          :confidence 0.9 :stake :low}
         extra))

(def ^:private req {:farmer-id "farmer-1"})

(deftest ok-log-work-record
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record) st)]
    (is (:ok? v))))

(deftest ok-schedule-farm-operation
  (let [st (fresh-store)
        v (governor/check req {} (op :schedule-farm-operation) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-threshold-boundary
  (testing "the supply-cost threshold escalate boundary is exclusive (over, not at)"
    (let [st (fresh-store)
          v (governor/check req {} (op :coordinate-supply-order :cost 300) st)]
      (is (:ok? v)))))

(deftest hard-on-unregistered-farmer
  (let [st (fresh-store)
        v (governor/check {:farmer-id "nobody"} {} (op :log-work-record) st)]
    (is (:hard? v))
    (is (some #(= :no-farmer (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-plot
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record :plot-id "P-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-plot (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-outside-closed-allowlist
  (let [st (fresh-store)
        v (governor/check req {} (op :dispatch-equipment) st)]
    (is (:hard? v))
    (is (some #(= :unknown-op (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-op-finalize-planting-decision
  (testing "finalizing a planting agronomic decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-planting-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-finalize-harvest-timing-decision
  (testing "finalizing a harvest-timing agronomic decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-harvest-timing-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-proceed-with-planting-decision
  (testing "proceeding with a planting decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :proceed-with-planting-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-proceed-with-harvest-decision
  (testing "proceeding with a harvest decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :proceed-with-harvest-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-override-farmer-crop-judgment
  (testing "overriding the farmer's own crop judgment is the farmer's exclusive judgment, never this actor's"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :override-farmer-crop-judgment) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-even-with-allowlisted-op
  (testing "defense-in-depth: a rationale that itself attempts to proceed with the planting decision is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :schedule-farm-operation)
                                           :rationale "recommend we proceed with the planting decision now")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-override-farmer
  (testing "defense-in-depth: a rationale attempting to override the farmer's crop judgment is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :log-work-record)
                                           :rationale "override the farmer's crop judgment and proceed")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest always-escalates-livelihood-concern-even-at-high-confidence
  (testing "a crop-failure-risk/food-security concern always requires human sign-off — the subsistence-farming livelihood dimension makes this a permanent escalation, never auto-commit-eligible"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :flag-livelihood-concern :concern-type :crop-failure-risk)
                                           :confidence 0.99)
                             st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-threshold
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :coordinate-supply-order :cost 500) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip-on-scope-exclusion
  (testing "the governor's scope-exclusion term list must never match the mock advisor's own default rationale text for any allowlisted op — CLAUDE.md's known self-tripping bug pattern (rationale legitimately contains bare nouns like 'planting'/'harvest'/'crop', but never the full finalization-action phrases)"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          ops [:log-work-record :schedule-farm-operation
               :flag-livelihood-concern :coordinate-supply-order]]
      (doseq [o ops]
        (let [request {:farmer-id "farmer-1" :op o :plot-id "P-1"
                        :stake :low :task "routine planting task"
                        :concern-type :crop-failure-risk :cost 150}
              proposal (advisor/-advise adv st request)
              v (governor/check request {} proposal st)]
          (is (not (:hard? v))
              (str o " proposal unexpectedly hard-blocked: " (:violations v))))))))
