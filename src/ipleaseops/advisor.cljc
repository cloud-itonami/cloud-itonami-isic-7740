(ns ipleaseops.advisor
  "IPLeaseAdvisor -- the *contained intelligence node* for the
  ISIC-7740 leasing-of-intellectual-property (patents, trademarks,
  franchise rights; NOT copyrighted works) operations-coordination
  actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: license-record logging, license-compliance-review
  scheduling, infringement/misuse-concern flagging, and royalty-report/
  payment-tracking coordination. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record and NEVER a direct actuation --
  every proposal's `:effect` is always `:propose`. Every output is
  censored downstream by `ipleaseops.governor` before anything touches
  the SSoT.

  This advisor NEVER finalizes a licensing grant and NEVER finalizes a
  royalty-rate determination -- those are permanently out of scope for
  this actor, not merely un-implemented. `ipleaseops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal
  for exactly this failure mode (a compromised or confused advisor
  drifting into scope it must never touch) and HARD-holds it,
  regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :license-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------
;;
;; Self-trip discipline: every rationale/summary string below is
;; written to describe what the proposal is (metadata logging,
;; scheduling, flagging, coordination) and explicitly disclaim that it
;; is NOT a licensing-grant or royalty-rate decision -- but using
;; phrasing distinct from `ipleaseops.governor/scope-excluded-terms`
;; (which match the finalization ACTION, e.g. "finalize the licensing
;; grant" / "ライセンス許諾を確定"), never colliding with them. See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
;; for the regression test that enforces this end-to-end.

(defn- propose-license-record
  "Draft a portfolio/licensee/usage-data license-record log entry.
  Pure metadata logging (patent/trademark/franchise ID, licensee,
  usage-report data) -- never a licensing-grant or royalty-rate
  decision."
  [_db {:keys [license-id patch]}]
  {:op         :log-license-record
   :license-id license-id
   :summary    (str license-id " のライセンス記録を記録: " (pr-str (keys patch)))
   :rationale  "IPポートフォリオ・ライセンシー・使用状況データのメタデータ記録のみ。ライセンスの許諾可否や使用料率の水準とは無関係。"
   :cites      [license-id]
   :effect     :propose
   :value      (merge {:license-id license-id} patch)
   :confidence 0.93})

(defn- propose-review-operation
  "Draft a license-compliance-review scheduling proposal (an internal
  ops calendar entry, never a binding licensing-grant decision)."
  [_db {:keys [license-id patch]}]
  {:op         :schedule-review-operation
   :license-id license-id
   :summary    (str license-id " のライセンス遵守レビュー日程調整を提案: " (pr-str (keys patch)))
   :rationale  "ライセンス遵守レビュー・使用状況確認の社内日程調整提案のみ。ライセンスを許諾するかどうかの判断ではない。"
   :cites      [license-id]
   :effect     :propose
   :value      (merge {:license-id license-id} patch)
   :confidence 0.88})

(defn- propose-infringement-concern
  "Surface a licensing-infringement, misuse, or unauthorized-use
  concern for HUMAN triage. This op ALWAYS escalates in
  `ipleaseops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real."
  [_db {:keys [license-id patch]}]
  {:op         :flag-infringement-concern
   :license-id license-id
   :summary    (str license-id " の侵害懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "特許・商標・フランチャイズ権の侵害疑義・不正使用に関する観察事実の報告。許諾可否の判断は常に人間が行う。"
   :cites      [license-id]
   :effect     :propose
   :value      (merge {:license-id license-id} patch)
   :confidence (or (:confidence patch) 0.85)})

(defn- propose-royalty-reporting
  "Draft a royalty-report/payment-tracking coordination proposal
  (collecting/reconciling licensee-reported usage and payment data
  only, never fixing what the rate itself should be)."
  [_db {:keys [license-id patch]}]
  {:op         :coordinate-royalty-reporting
   :license-id license-id
   :summary    (str license-id " のロイヤルティ報告・支払い追跡調整を提案: " (pr-str (keys patch)))
   :rationale  "ロイヤルティ報告・支払い追跡の社内調整提案のみ。料率がいくらであるべきかを判断するものではない。"
   :cites      [license-id]
   :effect     :propose
   :value      (merge {:license-id license-id} patch)
   :confidence 0.90})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-license-record (propose-license-record _db request)
                   :schedule-review-operation (propose-review-operation _db request)
                   :flag-infringement-concern (propose-infringement-concern _db request)
                   :coordinate-royalty-reporting (propose-royalty-reporting _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually decided to finalize the licensing grant and set the royalty rate")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t          :advisor-proposal
   :op         (:op proposal)
   :license-id (:license-id proposal)
   :summary    (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
