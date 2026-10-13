# Latest integration trial merge — 2026-10-13

The trial branch `codex/trial-v2-integration-latest` merges in this order:

1. `origin/feat/backend-v2-integration` at `d66f29d`.
2. Latest `origin/dev-backed` at `5a46eed` (including the v2 cutover and frontend Cypress work), recorded in merge `f14d80c`.
3. `origin/feat/database` at `ab4e0d2`.

This supersedes the earlier local trial `7890900`. The actual integration branch is not changed by this trial.

## Result

- Default packaged backend starts `ClinicV2Application` with the latest database V1 unchanged from `feat/database`.
- V2 preserves runtime columns, appointment states and atomic `REYYMMNNNN` receipt numbering; it also preserves Cash and split payment workflows.
- V3 supplies canonical reference data after V2 enum additions have committed. Demo users remain in an explicit optional seed location.
- Legacy SQL is isolated in `db/migration_old`. The importer maps latest enums/validity/receipt fields and creates entitlements before visit usages, then reconciles them against the ledger. Missing Thai IDs/phones stop import rather than inventing patient data. Only synthetic CI demo fixtures receive generated test IDs.
- Latest dev-backed security, patient editing, refunds, multi-item sales, locking/audits and all incoming API/browser specs remain present.
- Integration UI is retained with restored appointment actions, service/course sale selection and commission transaction navigation. Staff `position` is normalized at the frontend boundary. Sale cancellation responses expose the persisted reason/time for receipts and history.
- Closing displays real totals and reports success only after API confirmation. Sales start without demonstration discounts. Foreign patient names accept Unicode combining marks and optional passport data.
- CI uses the latest migrations, baseline version 3 for a manually imported schema, and runs on trial `codex/**` branches as well.

## Local verification

- Backend regression/API scenarios: 400 passed across 17 specs, including legacy and v2 boundary scenarios.
- Real PostgreSQL v2 API: 142 passed across 17 specs, including persisted cancellation metadata round trips.
- Frontend unit tests: 28 passed across 5 files; type check and production build passed.
- Frontend browser E2E on PostgreSQL: 97 passed across all 10 incoming specs; none skipped.
- `SCHEMA_CHECK_OK`: 45 entities, 21 controllers and 30 services wired against PostgreSQL 16.
- `RUNTIME_CHECK_OK`: fresh Flyway V1/V2/V3, manager provisioning, authentication/authorization, patient/service/course/payment/report contracts, paid+bonus entitlement and calendar-month validity.
- `V2_SMOKE_OK` and `MIGRATION_VERIFIED`: HTTP smoke and legacy import reconciliation, including sequence continuation.
- All seven workflow YAML files parse; `git diff --check` passes. GitHub Actions results are reported separately after pushing the trial branch.

Validation used disposable databases on a task-owned PostgreSQL container. No production database was changed. An already populated older v2 snapshot cannot be upgraded by replacing its Flyway V1 checksum; this trial validates a fresh latest schema and the documented legacy import path.
