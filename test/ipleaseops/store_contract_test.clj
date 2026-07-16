(ns ipleaseops.store-contract-test
  "Contract tests for `ipleaseops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [ipleaseops.store :as store]))

(deftest mem-store-license-lookup
  (testing "MemStore can store and retrieve license records by ID (string keys)"
    (let [licenses {"l1" {:license-id "l1" :name "Alice's Patent" :registered? true :verified? true}}
          s (store/mem-store licenses)]
      (is (some? (store/license s "l1")))
      (is (nil? (store/license s "l99"))))))

(deftest mem-store-all-licenses
  (testing "MemStore returns all license records in sorted order"
    (let [licenses {"l2" {:license-id "l2" :name "Bob's Trademark"}
                    "l1" {:license-id "l1" :name "Alice's Patent"}
                    "l3" {:license-id "l3" :name "Carol's Franchise"}}
          s (store/mem-store licenses)
          all-l (store/all-licenses s)]
      (is (= 3 (count all-l)))
      (is (= "l1" (:license-id (first all-l))))
      (is (= "l3" (:license-id (last all-l)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-licensing-log
  (testing "MemStore commit-record! appends to licensing-log"
    (let [s (store/mem-store {})
          record {:op :log-license-record :license-id "l1" :value {:licensee "Acme"}}]
      (is (= 0 (count (store/licensing-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/licensing-log s))))
      (is (= record (first (store/licensing-log s)))))))

(deftest mem-store-with-licenses
  (testing "MemStore with-licenses replaces the license directory"
    (let [s (store/mem-store {})
          new-licenses {"l1" {:license-id "l1" :name "Alice's Patent"}}]
      (is (= 0 (count (store/all-licenses s))))
      (store/with-licenses s new-licenses)
      (is (= 1 (count (store/all-licenses s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo license records"
    (let [s (store/seed-db)]
      (is (> (count (store/all-licenses s)) 0))
      (is (some? (store/license s "license-1")))
      (is (some? (store/license s "license-2")))
      (is (some? (store/license s "license-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for license-id"
    (let [demo (store/demo-data)
          licenses (:licenses demo)]
      (doseq [[k v] licenses]
        (is (string? k) "keys must be strings")
        (is (string? (:license-id v)) "license-id must be string")
        (is (= k (:license-id v)) "key must match license-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
