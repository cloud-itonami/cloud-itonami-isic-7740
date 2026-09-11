# cloud-itonami-isic-7740

**Leasing of intellectual property and similar products, except copyrighted works** — ISIC Rev.4 class 7740 (patents, trademarks, franchise rights — copyrighted works such as books/music/film are covered by different ISIC classes).

A coordination-only actor for IP-leasing back-office operations, behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Features

- **Closed proposal-op allowlist**: log-license-record, schedule-review-operation, flag-infringement-concern, coordinate-royalty-reporting (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **License record verified** — target IP-portfolio/license-agreement record must exist AND be registered/verified in the store.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — this actor NEVER finalizes a licensing grant and NEVER finalizes a royalty-rate determination. Any proposal whose content attempts to finalize either is permanently blocked. An op outside the closed four-op allowlist is folded into the same check.
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: license-record logging only (approval-gated)
  - Phase 2: + review scheduling, royalty-reporting coordination (approval-gated)
  - Phase 3: auto-commits clean, high-confidence proposals (infringement concerns always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## CRITICAL scope exclusions

This actor coordinates the *back office* around IP-leasing decisions — it never makes the decisions themselves. It structurally cannot:

- **Finalize a licensing grant** — who is licensed to practice a patent, use a trademark, or operate a franchise, and under what terms. That is always either a hard permanent block, or (for `:flag-infringement-concern`) an always-escalate op requiring human sign-off.
- **Finalize a royalty-rate determination** — locking in what rate a licensee actually pays. Same treatment.

The governor's `scope-excluded-terms` are deliberately phrased as the *finalization/execution action* ("finalize the licensing grant", "set the royalty rate"), never as a bare noun ("license", "licensing", "royalty", "rate"), because this actor's own legitimate happy-path proposals — especially `:flag-infringement-concern`, whose entire purpose is to talk *about* licensing/infringement concerns — routinely use those bare nouns. `governor-test` and `governor-contract-test` both assert the default mock-advisor proposals never self-trip this check.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
kbb -M:dev -P

# Run tests
kbb -M:dev:test

# Run linter
kbb -M:lint

# Run demo
kbb -M:run
```

## Test suite

- `test/ipleaseops/governor_test.cljk` — unit tests of governor hard checks, scope exclusion, and the self-trip regression test
- `test/ipleaseops/advisor_test.cljk` — advisor proposal shape and consistency
- `test/ipleaseops/phase_test.cljk` — rollout phase logic
- `test/ipleaseops/governor_contract_test.cljk` — full graph integration, audit trail
- `test/ipleaseops/store_contract_test.cljk` — Store protocol and MemStore implementation

## Modules

- `ipleaseops.store` — SSoT (MemStore, String-keyed license directory, append-only ledger)
- `ipleaseops.advisor` — contained intelligence node (mock + real-LLM seam)
- `ipleaseops.governor` — independent compliance layer
- `ipleaseops.phase` — staged rollout (0→3)
- `ipleaseops.operation` — langgraph-clj StateGraph
- `ipleaseops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-services) fleet. See ADR-2607121000, ADR-2607152500, and the per-actor coverage ADR in `com-junkawasaki/root` `90-docs/adr/` for design decisions.
