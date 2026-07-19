(ns ipleaseops.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (ipleaseops.operation -> ipleaseops.governor
  -> ipleaseops.store). No invented numbers, no timestamps, byte-identical
  across reruns.

  The scenario in `run-demo!` was checked against `ipleaseops.sim` first
  (its ids/ops were verified to actually exist in `store/demo-data` and
  `governor/allowed-ops` before reuse here -- unlike a sibling actor whose
  copy-pasted sim hard-held on every call because its ids didn't exist in
  that repo's real seed data). It mirrors `ipleaseops.sim`'s real license
  ids and ops, calling the OperationActor graph directly (rather than via
  `sim/-main`, which only prints to stdout) so the resulting store state
  can be rendered as HTML."
  (:require [clojure.string :as str]
            [ipleaseops.store :as store]
            [ipleaseops.operation :as op]
            [ipleaseops.phase :as phase]
            [ipleaseops.governor :as governor]
            [ipleaseops.advisor :as advisor]
            [langgraph.graph :as g]))

(def ^:private coordinator
  {:actor-id "coord-1" :actor-role :ip-licensing-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "ip-licensing-coordinator-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Drives the real OperationActor StateGraph through a scenario built
  directly from `store/demo-data` and `governor.cljc`'s actual rules.
  Returns the resulting db (a `Store`)."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; -- phase-3 auto-commit ops: governor-clean, high-confidence, and
    ;;    members of phase 3's :auto set (ipleaseops.phase/phases) --------
    (exec! actor "t1" {:op :log-license-record :license-id "license-1"
                        :patch {:licensee "Northgate Components Inc"
                                :usage-report-period "2026-Q3"}})
    (exec! actor "t2" {:op :schedule-review-operation :license-id "license-1"
                        :patch {:review-date "2026-09-18"
                                :scope "compliance-and-usage"}})
    (exec! actor "t3" {:op :coordinate-royalty-reporting :license-id "license-1"
                        :patch {:licensee "Northgate Components Inc"
                                :period "2026-Q3" :reported-units 42000}})

    ;; -- always-escalate op (governor/always-escalate-ops): high-stakes
    ;;    regardless of confidence, human coordinator signs off ----------
    (exec! actor "t4" {:op :flag-infringement-concern :license-id "license-1"
                        :patch {:concern "unlicensed use of the connector patent claims observed in a competitor teardown"
                                :confidence 0.9}})
    (approve! actor "t4")

    ;; -- HARD-hold, rule :license-unverified -- target record doesn't
    ;;    exist in the store at all ---------------------------------------
    (exec! actor "t5" {:op :log-license-record :license-id "license-99"
                        :patch {:licensee "Unknown Corp"}})

    ;; -- HARD-hold, rule :license-unverified -- target record exists but
    ;;    is registered? true / verified? false (license-3 in demo-data) -
    (exec! actor "t6" {:op :log-license-record :license-id "license-3"
                        :patch {:licensee "Late Autumn Franchise Group"}})

    ;; -- HARD-hold, rule :effect-not-propose -- advisor attempts a direct
    ;;    actuation (:effect :commit instead of :propose) ------------------
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                 (-advise [_ _ req]
                                                   (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "t7" {:op :schedule-review-operation :license-id "license-1"
                                 :patch {:review-date "2026-10-01"}}))

    ;; -- HARD-hold, rule :scope-excluded -- advisor drifts into the
    ;;    permanently-excluded licensing-grant/royalty-rate-finalization
    ;;    territory (governor/scope-excluded-terms) -------------------------
    (exec! actor "t8" {:op :log-license-record :license-id "license-1"
                        :out-of-scope? true :patch {}})

    db))

;; ----------------------------- render helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- last-fact-for [ledger license-id]
  (last (filter #(= license-id (:license-id %)) ledger)))

(defn- status-cell [fact]
  (cond
    (nil? fact)                          ["muted" "no activity yet"]
    (= :committed (:t fact))             ["ok" "committed"]
    (= :approval-granted (:t fact))      ["ok" "approval-granted"]
    (= :governor-hold (:t fact))         ["err" (str "governor-hold: " (str/join "," (map name (:basis fact))))]
    (= :approval-rejected (:t fact))     ["err" "approval-rejected"]
    (= :approval-requested (:t fact))    ["warn" "approval-requested"]
    :else                                ["muted" "in progress"]))

(defn- bool-cell [b]
  (if b ["ok" "yes"] ["err" "no"]))

(defn- license-rows [db]
  (let [ledger (store/ledger db)]
    (apply str
           (for [lic (store/all-licenses db)
                 :let [fact (last-fact-for ledger (:license-id lic))
                       [rcls rtxt] (status-cell fact)
                       [gcls gtxt] (bool-cell (:registered? lic))
                       [vcls vtxt] (bool-cell (:verified? lic))]]
             (str "<tr><td><code>" (esc (:license-id lic)) "</code></td>"
                  "<td>" (esc (:name lic)) "</td>"
                  "<td class=\"" gcls "\">" gtxt "</td>"
                  "<td class=\"" vcls "\">" vtxt "</td>"
                  "<td class=\"" rcls "\">" (esc rtxt) "</td></tr>\n")))))

(defn- committed-rows [db]
  (apply str
         (for [r (store/licensing-log db)]
           (str "<tr><td>" (esc (name (:op r))) "</td>"
                "<td><code>" (esc (:license-id r)) "</code></td>"
                "<td>" (esc (pr-str (sort (keys (:value r))))) "</td>"
                "<td>" (esc (or (:approved-by (:payload r)) "auto (phase-3, governor-clean)")) "</td></tr>\n"))))

(defn- action-gate-rows []
  (let [{:keys [writes auto]} (get phase/phases phase/default-phase)]
    (apply str
           (for [op (sort (map name governor/allowed-ops))
                 :let [opk (keyword op)
                       write? (contains? writes opk)
                       auto? (contains? auto opk)
                       escalate? (contains? governor/always-escalate-ops opk)]]
             (str "<tr><td><code>" (esc op) "</code></td>"
                  "<td class=\"" (if write? "ok" "err") "\">" (if write? "yes" "no") "</td>"
                  "<td class=\"" (if auto? "ok" "muted") "\">" (if auto? "yes" "no") "</td>"
                  "<td class=\"" (if escalate? "warn" "muted") "\">" (if escalate? "always" "no") "</td></tr>\n")))))

(defn- ledger-rows [db]
  (apply str
         (for [f (store/ledger db)]
           (let [detail (or (some->> (:basis f) (map name) (str/join ","))
                             (some->> (:reason f) name)
                             "")]
             (str "<tr><td>" (esc (name (:t f))) "</td>"
                  "<td>" (esc (name (:op f))) "</td>"
                  "<td><code>" (esc (:license-id f)) "</code></td>"
                  "<td>" (esc (or (:actor f) (:by f) "-")) "</td>"
                  "<td>" (esc (name (:disposition f))) "</td>"
                  "<td>" (esc detail) "</td></tr>\n")))))

(def ^:private css
  "table { width: 100%; border-collapse: collapse; font-size: 14px; }
.ok { color: #137a3f; }
body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }
header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }
h2 { margin-top: 0; font-size: 15px; }
.warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }
main { max-width: 980px; margin: 24px auto; padding: 0 20px; }
header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }
.muted { color: #888; font-size: 13px; }
.critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }
th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }
code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }")

(defn render [db]
  (str "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
       "<title>ipleaseops.render-html -- IP Leasing Operations Coordinator console</title>\n"
       "<style>\n" css "\n</style>\n</head>\n<body>\n"
       "<header class=\"bar\"><h1>IP Leasing Operations Coordinator -- Operator Console</h1>"
       "<span class=\"badge\">ISIC 7740 &middot; phase " (esc phase/default-phase) "</span></header>\n<main>\n"

       "<div class=\"card\"><h2>IP Portfolio / License Records</h2>\n"
       "<table><thead><tr><th>License ID</th><th>Name</th><th>Registered?</th><th>Verified?</th><th>Latest status</th></tr></thead>\n"
       "<tbody>\n" (license-rows db) "</tbody></table></div>\n"

       "<div class=\"card\"><h2>Committed Licensing Operations</h2>\n"
       "<table><thead><tr><th>Op</th><th>License ID</th><th>Value keys</th><th>Approved by</th></tr></thead>\n"
       "<tbody>\n" (committed-rows db) "</tbody></table></div>\n"

       "<div class=\"card\"><h2>Phase " (esc phase/default-phase) " Action Gate (governor/allowed-ops)</h2>\n"
       "<table><thead><tr><th>Op</th><th>Write-enabled</th><th>Auto-commit eligible</th><th>Always escalate</th></tr></thead>\n"
       "<tbody>\n" (action-gate-rows) "</tbody></table></div>\n"

       "<div class=\"card\"><h2>Audit Ledger (append-only, verbatim from store)</h2>\n"
       "<table><thead><tr><th>Type</th><th>Op</th><th>License ID</th><th>Actor/By</th><th>Disposition</th><th>Detail</th></tr></thead>\n"
       "<tbody>\n" (ledger-rows db) "</tbody></table></div>\n"

       "</main>\n</body></html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out)))
