(ns ipleaseops.store
  "SSoT for the ISIC-7740 leasing-of-intellectual-property (patents,
  trademarks, franchise rights; NOT copyrighted works) OPERATIONS
  COORDINATION actor, behind a `Store` protocol so the backend is a
  swap, not a rewrite -- the same seam every `cloud-itonami-isic-*`
  actor in this fleet uses.

  This actor coordinates the back-office operations of an IP-leasing
  desk: license-record logging (portfolio/licensee/usage data),
  license-compliance-review scheduling proposals, infringement- and
  misuse-concern flagging, and royalty-report/payment-tracking
  coordination. It NEVER directly finalizes a licensing grant or a
  royalty-rate determination -- see `ipleaseops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable
  block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `licenses` directory keyed by `:license-id` STRING (never
  a keyword -- consistent keying from the start, avoiding the
  silent-miss bug that plagued an earlier shepherd attempt in a sibling
  actor).

  A registered/verified IP-portfolio/license-agreement record must
  exist before ANY proposal for that record may ever commit or
  escalate -- `ipleaseops.governor`'s `license-unverified-violations`
  re-derives this from the record's own `:registered?`/`:verified?`
  fields, never from proposal self-report, the SAME 'ground truth, not
  self-report' discipline every sibling actor's own governor uses.

  The ledger stays append-only: which license record a proposal
  targeted, which operation, on what basis, committed/held/escalated
  and approved by whom is always a query over an immutable log.")

(defprotocol Store
  (license [s license-id] "Registered IP-portfolio/license-agreement
    record, or nil. License map: {:license-id .. :name .. :registered?
    bool :verified? bool}.")
  (all-licenses [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (licensing-log [s] "the append-only committed licensing-operation proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-licenses [s licenses] "replace/seed the license directory (map license-id->license)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained license directory covering both the happy
  path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:licenses
   {"license-1" {:license-id "license-1"
                 :name "Patent Portfolio -- Modular Battery Connector (US10,xxx,xxx family)"
                 :registered? true :verified? true}
    "license-2" {:license-id "license-2"
                 :name "Trademark Licensing -- Kanda Coffee Roasters (word + logo marks)"
                 :registered? true :verified? true}
    "license-3" {:license-id "license-3"
                 :name "Franchise Rights -- Late Autumn Bakery concept (in intake)"
                 :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (license [_ license-id] (get-in @a [:licenses license-id]))
  (all-licenses [_] (sort-by :license-id (vals (:licenses @a))))
  (ledger [_] (:ledger @a))
  (licensing-log [_] (:licensing-log @a))
  (commit-record! [_ record]
    (swap! a update :licensing-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-licenses [s licenses] (when (seq licenses) (swap! a assoc :licenses licenses)) s))

(defn seed-db
  "A MemStore seeded with the demo license directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :licensing-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `licenses` map (license-id string
  -> license map) -- the primary test/dev entry point. `licenses` may
  be empty (an unregistered-everywhere store)."
  [licenses]
  (->MemStore (atom {:licenses (or licenses {}) :ledger [] :licensing-log []})))
