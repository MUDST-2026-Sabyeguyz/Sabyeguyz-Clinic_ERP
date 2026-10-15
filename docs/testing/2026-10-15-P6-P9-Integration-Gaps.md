# P6–P9 integration: status and remaining work

Date: 15 October 2026 (Asia/Bangkok). Target: `feat/backend-v2-integration`.

## Verified Git state before integration

| Ref | SHA | Delivered scope |
|---|---|---|
| target, local and origin | `844e83a10c6995fc3752cde81a582c70db18ecef` | P6–P9 handoff base |
| `feat/p6-p7-identifiers-courses`, local and origin | `edfc4e683bc1f89bc433f34fbdc3c0bde5a25a68` | P7 A1 course usage/transfer fix and regression; A2/A3 unfinished |
| `feat/p8-p9-reports-release`, local and origin | `d70a909345c63b01aee864ecb27f4f6abe6c55c6` | P8 dashboard Q11, P9 migration rehearsal/reconciliation tooling; release unfinished |

The source branches share base `b6473b8`. Before this document commit the target was `844e83a`; it now contains this documentation only. Neither source branch was merged. An attempted local P8/P9 merge was rejected by automatic approval review because this broad integration would occur before the previously required completion/check/review gates. No merge commit, remote push, deployment, or production migration resulted. This document records the partial deliveries; the branch names do not imply completed P6–P9.

## Delivered and previously tested on separate branches

- P6/P7: A1 adds persisted treating therapist name to course usage and charges an appointment-linked use to the checked-in patient. The teammate reports 223/223 backend HTTP, 51/51 unit, 117/117 browser checks on their branch; these are teammate results, not a combined-tree run. See their `docs/testing/2026-10-14-P6-P7-Progress.md` on `edfc4e6`.
- P8: Dashboard Q11 uses registration date for new customers, pre-range registration plus in-range attendance for returning customers, and numeric counts for renewals and Back Office course-template edits. Sales are by receipt sale date and cash by successful payment date. Previous local evidence on `0de5e2a`/`d70a909`: 226/226 backend HTTP, 51/51 unit, 120/120 browser, package/build, fresh runtime and SchemaCheck. See `docs/testing/2026-10-15-P8-Dashboard-Q11.md` on `d70a909`.
- P9: repeatable disposable PostgreSQL legacy import, reconciliation, FK and accounting checks, negative probes, and migrated HTTP/DB readback. The fixture has legacy receipt headers totaling 45,800 THB versus imported line-based receipts totaling 47,300 THB: 15 receipts differ by 1,500 THB. `releaseReady=false`; the original values require a policy decision, not an inferred approval. See `docs/testing/2026-10-15-P8-P9-Acceptance.md` and artifact JSON on `d70a909`.

The P6/P7 and P8/P9 branches both edit `frontend/src/api/client.ts`; a future merge must retain **both** `CourseUsageTransaction.therapistName` and the additive `DashboardResponse` fields. The combined tree has not been built or tested.

## Missing decisions and implementation

| Area | Needed before calling it complete |
|---|---|
| P6 numbering | Q3: exact nine-digit HN components/year/branch/month/run, reset, capacity and old numbers. Q4: receipt sequence scope, persisted/display ID, uniqueness, overflow and legacy mapping. Implement forward migration, service, contract and boundary/concurrency tests after approval. |
| P7 course lifecycle | Q7: expiry day boundary, role, extension base and transfer-member impact. Q13: attachment purpose, owner, MIME/size/count, storage, access, deletion and retention. Finish expiry mutation/readback, print/reprint course usage with owner HN/name/therapist, and decide transferred-member walk-in usage contract. |
| P8 reporting and adjacent work | Q9 report actor/branch permission matrix. Q12 Calendar owner/branch account, inbound/outbound conflict and delete policy, privacy and live sync; room busy/repair states; password-reset delivery and verification. Existing Calendar code needs an approved behavior audit and permitted live Google test. |
| P9 release | Q10 active-schema mapping and retention for allergy/consent/emergency legacy data. Resolve header-versus-line receipt differences; repeat migration/reconciliation on the approved final P6/P7 schema and a permitted real snapshot. Verify backup/rollback, hosted CI, and independent review before release sign-off. |

## Jenkins verification status

**No Jenkins build was run for these branch heads or a combined revision.** `docker ps` showed only the disposable PostgreSQL container `clinic-p8p9-20261015`; no Jenkins service was running. The checked-in `jenkins-backup/Jenkinsfile` and `Refresh-Source.ps1` checkout/bundle **`develop` only**, so triggering the existing job would test a different SHA. The job also defaults `DEPLOY=true`; this request authorizes testing, not deployment.

After the merge is allowed and the combined revision exists, configure an isolated test-only Jenkins job/snapshot for the exact `feat/backend-v2-integration` SHA, disable deployment (`DEPLOY=false`), record Jenkins build number and `source-sha.txt`, and verify backend/frontend unit/build plus PostgreSQL API/browser regression, migration rehearsal, RuntimeCheck and SchemaCheck. Do not call a `develop` build acceptance of this integration. Preserve the source branches and the target until the merged revision passes.
