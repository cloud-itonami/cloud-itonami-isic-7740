# Contributing to cloud-itonami-isic-7740

Contributions should preserve the actor's scope: back-office IP-leasing
coordination only, with CRITICAL exclusions of directly finalizing a
licensing grant or a royalty-rate determination (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: clojure -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize a licensing grant (who is licensed to practice a patent,
  use a trademark, or operate a franchise, under what terms).
- Finalize a royalty-rate determination (locking in what rate a
  licensee actually pays).
- Perform license-agreement negotiation, contract drafting, or any
  legally binding execution.

Contributions that cross these boundaries will be rejected.
