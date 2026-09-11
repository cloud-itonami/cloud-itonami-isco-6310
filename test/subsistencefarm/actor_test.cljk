(ns subsistencefarm.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [subsistencefarm.actor :as actor]
            [subsistencefarm.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-farmer! st {:farmer-id "farmer-1" :name "Kobo Yamada"})
    (store/register-plot! st {:plot-id "P-1" :name "Kobo Household Plot" :max-supply-cost 300})
    st))

(deftest commits-a-registered-work-log
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:farmer-id "farmer-1" :op :log-work-record :stake :low
                  :plot-id "P-1" :task "planting progress log"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "farmer-1"))))))

(deftest holds-an-unregistered-plot-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:farmer-id "farmer-1" :op :log-work-record :stake :low
                  :plot-id "P-ghost" :task "planting progress log"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "farmer-1")))))

(deftest interrupts-then-approves-livelihood-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:farmer-id "farmer-1" :op :flag-livelihood-concern :stake :low
                  :plot-id "P-1" :concern-type :crop-failure-risk}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "farmer-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "farmer-1")))))))

(deftest holds-a-scope-excluded-op-even-at-high-confidence
  (testing "an actor run can never commit a proposal that would finalize a planting/harvest-timing agronomic decision, regardless of disposition path"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:farmer-id "farmer-1" :op :finalize-planting-decision :stake :low
                    :plot-id "P-1" :task "planting decision"}
          result (actor/run-request! graph request {} "thread-4")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farmer-1"))))))

(deftest holds-an-override-farmer-crop-judgment-proposal
  (testing "an actor run can never commit a proposal that would override the farmer's own judgment about their household's crops"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:farmer-id "farmer-1" :op :override-farmer-crop-judgment :stake :low
                    :plot-id "P-1" :task "override farmer decision"}
          result (actor/run-request! graph request {} "thread-5")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farmer-1"))))))
