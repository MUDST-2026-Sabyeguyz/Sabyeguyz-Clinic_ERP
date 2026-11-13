# Frontend / feat/database integration audit

## Evidence and boundary

- Target inspected: local `origin/feat/database`, commit `a00b1f6`, matching the supplied handoff. Remote freshness has not been rechecked in this audit.
- Existing working branch: `fix-frontend`. Do not merge backend or migrations as a shortcut.
- Existing Docker backend connects to `jdbc:postgresql://postgres:5432/clinic_erp_seed_v2`. This is a local container, but not a newly isolated test environment; its contents and provenance are not certified here.
- Existing frontend/backend/Postgres occupy ports 80/8080/5432. No containers, database contents or application source were changed by this audit.

## Confirmed integration gaps

| Area | Current frontend | Target contract / required work |
| --- | --- | --- |
| Appointment create/reschedule | API request types omit endAt | Require startAt/endAt with Bangkok offset; three statuses; new appointments UNCONFIRMED |
| Status actions | Both appointment views call confirm/checkIn/complete | Open treatment workflow; backend confirms only after successful sale/usage; cancel remains distinct |
| NEW registration | Both registration views call linkPatient | Preserve appointmentId and registered patientId in workflow without changing original NEW appointment |
| Progress | Client has progress plus settle/link-documents/verify-no-documents; process panel calls old mutations | Replace old model with FOUND/NO_LINKED_RECORD/REVIEW_REQUIRED and sales/usages arrays; no automatic retry POST |
| Single visit payment | Verify all callers, not only client types | Send appointmentId, branchCode, treatingStaffId, paymentLabelCode; exactly one completed payment covering accepted net amount |
| Course purchase | Client purchase request only declares patient/course/branch | Add chosen label and owner/seller fields supported by target; purchase alone is not a visit |
| Course usage | Client use omits patientId/treatingStaffId; no batch method | Explicit actual patient and therapist, batch transaction for multiple usages; mixed sale carries courseUsages |
| Renewal | CourseRenewalDialog uses renewals contract | Keep unavailable pending review; do not silently reinterpret as extension approval |
| Commission preview | Target requires branchCode | Trace form dependencies and stale-response protection for course/branch/seller/price/discount |
| Commission close/payment | Target uses month + branch and array responses | Trace every caller; branch closing status is independent of individual rows; preserve null amounts |
| Users/permissions | Client email optional | Required real email; distinguish override from role; handle current-account 401/403 |
| Reports/calendar | Requires semantic audit | Stored appointment end time; separate appointment date from completed-visit date; cancellation does not refund/reverse usage |

The table identifies confirmed gaps and remaining trace tasks. It is not a claim that every endpoint/caller has been audited or that runtime compatibility is established.

## Blocking migration conflict

Staged `V1010__appointment_process_links.sql` is incompatible with the target branch. Target V1010 is `V1010__add_fee_rule_version_and_appointment_contact_middle_name.sql`, followed by V1011–V1020. Do not apply either migration set to the existing database as part of frontend work. Preserve staged user work; do not delete, renumber or unstage it without agreement.

## Proposed implementation approach (awaiting confirmation)

Use target-only frontend contracts rather than maintaining two divergent finance workflows. Keep the current UI layout and shared role/branch restrictions. Implement in order: appointment and registration; payments/usage/progress; commission; settings/users/reports. Add contract-focused tests for every phase and build after coherent changes. Do not claim end-to-end success until the target backend is running on the isolated database.

## Isolated runtime proposal

Prefer a separate Docker Compose project `clinic-frontend-test` with dedicated network, PostgreSQL volume and database `clinic_frontend_test`. Bind test Postgres/backend/frontend to loopback ports 55432/18080/18081 after verifying availability. Build the backend from a clean, pinned target checkout, not the current backend containing appointment_process. Current containers and volumes remain untouched. Seed synthetic test records only; do not import team/patient data. Disable Google sync by default to prevent local tests altering real calendars. Use local-only credentials kept out of Git.

Alternative: add a separate database in the current PostgreSQL container; cheaper, but shares the container lifecycle and is easier to target accidentally. Separate project is recommended for stronger isolation.

Creating the runtime and automatically applying target Flyway migrations requires confirmation. No reset, volume removal, data import or deployment is authorized by this document.
