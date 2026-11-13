# Trial merges across remote branches — 2026-11-11

Baseline: draft PR #67, `merging/prepare-for-final` at `5947f0106dc40bc4611aecccd0bbf216830ba0a0`, targeting `main` at `e172add379c54d8ba3a58df400d428bbf4dff85c`.

This is a **read-only, independent** `git merge-tree --write-tree --name-only` trial of each remote branch against the PR head. “Contained” means ancestry-contained. “Clean trial” means Git reports no textual merge conflict for that one branch. “Conflict” gives the count of Git conflict messages. These trials did **not** integrate the branch, resolve semantics, run tests, or check how multiple new branches interact when merged sequentially.

| Remote branch | SHA | Result | Conflict entries |
|---|---|---|---:|
| `api_schema` | `26ce8e09c3` | contained | 0 |
| `backup/draft-phase1` | `6910419c7d` | conflict | 9 |
| `backup/present-environment` | `ba9ca1be50` | conflict | 5 |
| `codex/cypress-e2e-coverage` | `91d6929f89` | contained | 0 |
| `codex/jenkins-worker-init` | `ac83eeed70` | conflict | 7 |
| `codex/non-commission-backend` | `ddf2ce5957` | contained | 0 |
| `dev` | `a4b2055f5e` | contained | 0 |
| `dev-backed` | `ade2718178` | contained | 0 |
| `develop` | `a62ff177a6` | conflict | 75 |
| `feat/backend-v2-integration` | `a08b9ee695` | contained | 0 |
| `feat/calendar` | `5240622e2f` | contained | 0 |
| `feat/ci-schema-validate` | `530670c823` | contained | 0 |
| `feat/commission` | `8038fd995b` | contained | 0 |
| `feat/controller` | `559dc95174` | contained | 0 |
| `feat/dashboard-and-appointment-lock-backend` | `76aa7dfda3` | clean trial | 0 |
| `feat/database` | `4d54c5e470` | contained | 0 |
| `feat/e2e-therapist` | `a2dcb02298` | contained | 0 |
| `feat/google-busy-times` | `891281e44c` | contained | 0 |
| `feat/p6-p7-identifiers-courses` | `675e3c194f` | clean trial | 0 |
| `feat/p8-p9-reports-release` | `54175a207c` | contained | 0 |
| `feat/req-oct-appointments-settings` | `72d4c6f601` | contained | 0 |
| `feat/schema-v2-align` | `f51f51f7d8` | contained | 0 |
| `feat/seed-legacy-demo-data` | `ac375d3c2b` | contained | 0 |
| `feature/appointments` | `1d1e6fdbf6` | contained | 0 |
| `feature/commission-manager` | `2810435d81` | contained | 0 |
| `feature/commission-therapist` | `1d07b41604` | contained | 0 |
| `feature/dashborad` | `9ff0d69137` | contained | 0 |
| `feature/patients` | `1ed0fa14d3` | conflict | 17 |
| `feature/personnel` | `d31c34f68f` | conflict | 2 |
| `feature/reports` | `c3c981abcb` | contained | 0 |
| `feature/services` | `a2841b84ac` | contained | 0 |
| `feature/settings` | `1b1f3e22dc` | contained | 0 |
| `feature/transaction` | `02b3fbf98b` | contained | 0 |
| `fix-frontend` | `3bbc90927d` | clean trial | 0 |
| `fix/develop-frontend-build-types` | `5ae8bd7a01` | conflict | 75 |
| `fix/e2e-baseline-failures` | `9f605e641f` | contained | 0 |
| `hotfix/merging` | `1213eb4d40` | contained | 0 |
| `new_calendar` | `0cb65b00db` | contained | 0 |
| `test-merge` | `ec0b692ad0` | contained | 0 |

## Next integration decisions

- The three clean trials (`feat/dashboard-and-appointment-lock-backend`, `feat/p6-p7-identifiers-courses`, `fix-frontend`) still require review for duplicate/reverted behavior and sequential merge testing before committing.
- The seven conflicting refs include older backup and develop variants. Resolve them against current requirements; do not choose “ours” or “theirs” globally to force a green merge.
- `feat/ops` and `fix/sabye-safe-demo-and-deploy` are local-only, outside this remote-branch matrix. The active `feat/ops` worktree also has an untracked Cypress file. Preserve and review those before integration.
- Re-run this matrix after any source branch changes. A draft PR against `main` does not automatically fetch or merge updates from other branches.
