# P6–P9 integration: status and remaining work

Date: 15 October 2026 (Asia/Bangkok). Target: `feat/backend-v2-integration`.

## Verified Git state and local merge

| Ref | SHA | Delivered scope |
|---|---|---|
| target before merge, local and origin | `844e83a10c6995fc3752cde81a582c70db18ecef` | P6–P9 handoff base |
| `feat/p6-p7-identifiers-courses`, local and origin | `edfc4e683bc1f89bc433f34fbdc3c0bde5a25a68` | P7 A1 course usage/transfer fix and regression; A2/A3 unfinished |
| `feat/p8-p9-reports-release`, local and origin | `d70a909345c63b01aee864ecb27f4f6abe6c55c6` | P8 dashboard Q11, P9 migration rehearsal/reconciliation tooling; release unfinished |

The source branches share base `b6473b8`. After the user explicitly approved merging partial work, the local target merged P8/P9 in `20e489f` and P6/P7 in `dc5d815`; Jenkins support commits `19a9ae7` and `1a97a8a` follow. The merge resolved `frontend/src/api/client.ts` automatically and retains **both** `CourseUsageTransaction.therapistName` and the additive `DashboardResponse` fields. Read-only integration review found no new defect in the changed course usage, Dashboard and shared type paths. A later read-only remote check found `origin/feat/backend-v2-integration` at `1a97a8a`, including both merges; Alice did not issue a push command in this run. This document's later commit remains local. No deployment or production migration occurred. Branch names and successful integration tests do not imply completed P6–P9.

## Delivered scope

- P6/P7: A1 adds persisted treating therapist name to course usage and charges an appointment-linked use to the checked-in patient. The teammate reports 223/223 backend HTTP, 51/51 unit, 117/117 browser checks on their branch; these are teammate results, not a combined-tree run. See their `docs/testing/2026-10-14-P6-P7-Progress.md` on `edfc4e6`.
- P8: Dashboard Q11 uses registration date for new customers, pre-range registration plus in-range attendance for returning customers, and numeric counts for renewals and Back Office course-template edits. Sales are by receipt sale date and cash by successful payment date. Previous local evidence on `0de5e2a`/`d70a909`: 226/226 backend HTTP, 51/51 unit, 120/120 browser, package/build, fresh runtime and SchemaCheck. See `docs/testing/2026-10-15-P8-Dashboard-Q11.md` on `d70a909`.
- P9: repeatable disposable PostgreSQL legacy import, reconciliation, FK and accounting checks, negative probes, and migrated HTTP/DB readback. The fixture has legacy receipt headers totaling 45,800 THB versus imported line-based receipts totaling 47,300 THB: 15 receipts differ by 1,500 THB. `releaseReady=false`; the original values require a policy decision, not an inferred approval. See `docs/testing/2026-10-15-P8-P9-Acceptance.md` and artifact JSON on `d70a909`.

## Combined revision verification

The source and CI changes were tested at **`1a97a8a4b5550b5cceffa929e2bd20bbd18275af`** on `feat/backend-v2-integration` (before this documentation update).

| Check | Result |
|---|---|
| Jenkins `Clinic-backup` build #3, source-sha artifact matches above; `DEPLOY=false` | **SUCCESS**; Maven `verify`, frontend unit **51/51** and build, backend/frontend image build, disposable PostgreSQL services healthy, browser login smoke **2/2**; deploy stage skipped |
| `bash scripts/run-all-tests.sh all` on combined checkout | **ALL TESTS PASSED**; ESLint, frontend unit, type-check/build, backend HTTP Cypress **232/232**, frontend browser Cypress **120/120**; throwaway PostgreSQL/backend cleaned by script |
| `node --test scripts/legacy-migration/ci/rehearse.test.cjs` | **3/3** |
| P9 rehearsal on `clinic-p8p9-20261015` disposable PostgreSQL | `MIGRATION_REHEARSAL_OK`, two independent restores identical; six corruptions detected and rolled back; populated target rejected; `releaseReady=false` |
| Fresh PostgreSQL RuntimeCheck and SchemaCheck | `RUNTIME_CHECK_OK`, `SCHEMA_CHECK_OK`; 45 entities, 22 controllers and 32 services wired |

The rehearsal retained its three disposable databases for inspection. It again found the 45,800/47,300 THB receipt-total difference described above. Jenkins's checked-in pipeline tests only the browser login smoke; the full v2 API/browser counts are from the separate repository test script on the same checked-out revision.

## Missing decisions and implementation

| Area | Needed before calling it complete |
|---|---|
| P6 numbering | Q3: exact nine-digit HN components/year/branch/month/run, reset, capacity and old numbers. Q4: receipt sequence scope, persisted/display ID, uniqueness, overflow and legacy mapping. Implement forward migration, service, contract and boundary/concurrency tests after approval. |
| P7 course lifecycle | Q7: expiry day boundary, role, extension base and transfer-member impact. Q13: attachment purpose, owner, MIME/size/count, storage, access, deletion and retention. Finish expiry mutation/readback, print/reprint course usage with owner HN/name/therapist, and decide transferred-member walk-in usage contract. |
| P8 reporting and adjacent work | Q9 report actor/branch permission matrix. Q12 Calendar owner/branch account, inbound/outbound conflict and delete policy, privacy and live sync; room busy/repair states; password-reset delivery and verification. Existing Calendar code needs an approved behavior audit and permitted live Google test. |
| P9 release | Q10 active-schema mapping and retention for allergy/consent/emergency legacy data. Resolve header-versus-line receipt differences; repeat migration/reconciliation on the approved final P6/P7 schema and a permitted real snapshot. Verify backup/rollback, hosted CI, and independent review before release sign-off. |

## Jenkins run notes and limits

The local Jenkins setup was created for this verification. Build #1 reached Maven/frontend/build and login **2/2**, then failed in post-actions because the Jenkins image lacked its declared `junit` step; cleanup did not run. Added the `junit` plugin. Build #2 passed build stages but failed when the temporary `clinic-ci-1` database still held port 15432. Its exact disposable Compose project and env file were checked, then cleaned. Build #3 passed with the corrected image and no deployment. These first two runs are infrastructure failures, not product-test successes or evidence that the final build failed.

`jenkins-backup/Jenkinsfile` now accepts the selected branch from a verified local bundle, and the seeded job exposes parameters before its first build with `DEPLOY=false` by default. The original `develop` workflow remains available. `jenkins-backup/README_TH.md` records the integration-bundle procedure. Jenkins build #3 artifact `source-sha.txt` verifies the tested revision; later documentation commits are not part of that build. This was a local Jenkins run, not hosted Actions or a production deployment.
