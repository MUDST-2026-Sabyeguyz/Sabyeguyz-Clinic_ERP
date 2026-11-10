# Backend main alignment

Goal: Import the backend contract documented in D:/3_y3/sabyeDoc/README-FRONTEND-ALL-BACKEND-UPDATES-2026-10-25.md into the current checkout, as requested by the user.

Baseline: local main 00873c79ee5d7881361b18eaea42e3f883903912. Its backend is identical to handoff baseline b73f8cf. No remote freshness claim.

Scope: backend/src and backend/cypress from main; preserve patient persistence, permission CRUD/catalog, and auth-version support. Leave frontend and the Git index untouched. Do not migrate or restart the user's existing database/runtime.

- [x] Audit baseline and save backend source, tests, staged/unstaged diffs outside the project migration path.
- [x] Import main source/migrations/tests and merge support patches with main validation and security behavior.
- [x] Verify imported bytes, migration uniqueness, compile/package and focused API checks on a disposable database if available.
- [x] Record verification and remaining runtime limitations.

Backup: D:/3_y3/sabyeDoc/backups/2026-10-31-backend-main-alignment

Review focus: preserve patient fields; preserve effective permission enforcement; reject stale auth-version tokens while retaining database role lookup; remove old process/renewal sources; do not apply changed migration identities to an existing database.

Verification: final clean package passed (399 main/33 test sources; Surefire skipped); 546 exact main blob matches; 26 migrations without duplicate versions; Flyway/Hibernate startup passed on disposable PostgreSQL through V1022; Task1, Task2 (11), Task3 (5), support (7) API checks passed. Independent review found no important integration regressions. Git index unchanged. Full report: D:/3_y3/sabyeDoc/2026-10-31-backend-main-import-verification.md.
