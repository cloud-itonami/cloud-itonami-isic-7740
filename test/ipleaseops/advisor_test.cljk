(ns ipleaseops.advisor-test
  "Unit tests of `ipleaseops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [ipleaseops.advisor :as adv]
            [ipleaseops.store :as store]))

(def db (store/seed-db))

(deftest propose-license-record-shape
  (testing "license-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-license-record
                           :license-id "license-1"
                           :patch {:licensee "Acme Robotics KK" :usage-report-period "2026-Q2"}})]
      (is (= :log-license-record (:op p)))
      (is (= "license-1" (:license-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :license-id)))))

(deftest propose-review-operation-shape
  (testing "review-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-review-operation
                           :license-id "license-2"
                           :patch {:review-date "2026-09-18"}})]
      (is (= :schedule-review-operation (:op p)))
      (is (= "license-2" (:license-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-infringement-concern-shape
  (testing "infringement-concern proposal has correct shape"
    (let [p (adv/infer db {:op :flag-infringement-concern
                           :license-id "license-1"
                           :patch {:concern "unlicensed use observed in a competitor teardown"}})]
      (is (= :flag-infringement-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-royalty-reporting-shape
  (testing "royalty-reporting proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-royalty-reporting
                           :license-id "license-1"
                           :patch {:licensee "Northgate Components Inc" :period "2026-Q3"}})]
      (is (= :coordinate-royalty-reporting (:op p)))
      (is (= :propose (:effect p)))
      (is (>= (:confidence p) 0.85)))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-license-record :schedule-review-operation
                :flag-infringement-concern :coordinate-royalty-reporting]]
      (let [p (adv/infer db {:op op :license-id "license-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-license-record :schedule-review-operation
                :flag-infringement-concern :coordinate-royalty-reporting]]
      (let [p (adv/infer db {:op op :license-id "license-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
