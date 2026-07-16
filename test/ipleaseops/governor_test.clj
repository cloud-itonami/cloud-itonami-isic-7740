(ns ipleaseops.governor-test
  "Pure unit tests of `ipleaseops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [ipleaseops.advisor :as advisor]
            [ipleaseops.governor :as gov]
            [ipleaseops.store :as store]))

(def license-1 {:license-id "license-1" :name "Modular Battery Connector patent portfolio" :registered? true :verified? true})
(def license-3 {:license-id "license-3" :name "Late Autumn Bakery franchise rights" :registered? true :verified? false})

(defn- clean-proposal [op license-id]
  {:op op :license-id license-id :summary "s" :rationale "routine IP-leasing operations coordination"
   :cites [license-id] :effect :propose :value {} :confidence 0.85})

(deftest license-unregistered-is-hard
  (testing "no license record at all -> HARD hold"
    (let [s (store/mem-store {"license-1" license-1})
          verdict (gov/check {} nil (clean-proposal :log-license-record "unknown-license") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:license-unverified} (map :rule (:violations verdict)))))))

(deftest license-unverified-is-hard
  (testing "license record registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"license-3" license-3})
          verdict (gov/check {} nil (clean-proposal :log-license-record "license-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:license-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"license-1" license-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-review-operation "license-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"license-1" license-1})
          verdict (gov/check {} nil (clean-proposal :grant-license-directly "license-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest licensing-grant-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale finalizes a licensing grant is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"license-1" license-1})
          poisoned (assoc (clean-proposal :log-license-record "license-1")
                          :rationale "decided to finalize the licensing grant and authorize the license agreement for territory JP"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest royalty-rate-finalization-content-is-hard
  (testing "a proposal touching finalizing a royalty-rate determination is HARD-blocked, same as licensing grants"
    (let [s (store/mem-store {"license-1" license-1})
          poisoned (assoc (clean-proposal :schedule-review-operation "license-1")
                          :rationale "decided to set the royalty rate and finalize royalty rate for Q4 reporting"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest royalty-rate-japanese-finalization-content-is-hard
  (testing "a proposal touching finalizing a royalty rate in Japanese is HARD-blocked"
    (let [s (store/mem-store {"license-1" license-1})
          poisoned (assoc (clean-proposal :coordinate-royalty-reporting "license-1")
                          :summary "ロイヤルティ料率を確定してから支払い追跡を締める")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest license-grant-content-in-value-is-hard
  (testing "a proposal whose draft value grants the license is HARD-blocked"
    (let [s (store/mem-store {"license-1" license-1})
          poisoned (assoc (clean-proposal :log-license-record "license-1")
                          :value {:decision "grant the license to Northgate Components Inc"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-infringement-concern-is-not-scope-excluded
  (testing "flagging a possible infringement/misuse concern (not a finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"license-1" license-1})
          concern (assoc (clean-proposal :flag-infringement-concern "license-1")
                         :value {:concern "unlicensed use of patent claims observed in a competitor teardown"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (infringement/misuse doubts) is exactly what this op exists to surface"))))

;; ----------------------------- self-trip regression (mandatory) -----------------------------
;;
;; A known bug class in this exact codebase family: a governor's own
;; scope-exclusion term list phrased as a bare noun can accidentally
;; match inside the mock advisor's own DEFAULT rationale/disclaimer
;; text for a legitimate, allowed proposal -- causing the actor to
;; self-block on its own happy path. This actor's `scope-excluded-terms`
;; are deliberately phrased as the finalization/execution ACTION
;; ('finalize the licensing grant', not bare 'license grant'; 'set the
;; royalty rate', not bare 'royalty rate'). This test asserts the
;; default mock advisor's own proposals for all four allowed ops, for a
;; clean registered+verified license record, NEVER trip
;; scope-exclusion -- i.e. the actor never self-blocks on its own happy
;; path.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "none of the four default proposal generators' own rationale/summary/value text self-trips scope-exclusion"
    (let [s (store/mem-store {"license-1" license-1})]
      (doseq [op [:log-license-record :schedule-review-operation
                  :flag-infringement-concern :coordinate-royalty-reporting]]
        (let [proposal (advisor/infer nil {:op op :license-id "license-1"
                                            :patch {:licensee "Northgate Components Inc"
                                                    :usage-report-period "2026-Q3"
                                                    :review-date "2026-09-18"
                                                    :period "2026-Q3"
                                                    :reported-units 42000
                                                    :concern "possible unlicensed use"}})
              verdict (gov/check {:license-id "license-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default proposal for op " op " must never self-trip scope-exclusion; got violations: "
                   (:violations verdict)))
          (is (not (:hard? verdict))
              (str "default proposal for op " op " (clean, registered+verified license record) must never HARD hold")))))))
