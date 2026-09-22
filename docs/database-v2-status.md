# Database v2 Integration Status

> **Last updated:** 2026-09-22 — Branch `xan`, HEAD commit after Step 17

The `database-v2` Spring profile is the Compose runtime profile. Flyway applies
only `db/migration_new/V1__initial_schema.sql`. Legacy JPA entities are isolated
under the `!database-v2` profile and must never be used by v2 controllers.

---

## ✅ Integration Complete

All 17 steps of the Database v2 integration are fully implemented, verified,
and committed on branch `xan`.

### Backend — 281 tests PASS (0 failures, 0 skipped)

| Slice | Controller / Service | Profile annotation | Status |
|---|---|---|---|
| Auth & Login | `DatabaseV2AuthController` | `@Profile("database-v2")` | ✅ DONE |
| Branch Management | `DatabaseV2BranchController` | `@Profile("database-v2")` | ✅ DONE |
| Staff & Therapists | `DatabaseV2StaffController` | `@Profile("database-v2")` | ✅ DONE |
| Master Data & Lookups | `DatabaseV2MasterDataController` | `@Profile("database-v2")` | ✅ DONE |
| Patient Registration & HN | `DatabaseV2PatientController` | `@Profile("database-v2")` | ✅ DONE |
| Appointments & Visit Lifecycle | `DatabaseV2AppointmentController` | `@Profile("database-v2")` | ✅ DONE |
| Sales, Receipts & Payments | `DatabaseV2SaleController` | `@Profile("database-v2")` | ✅ DONE |
| Course Ledger & Transfers | `DatabaseV2CourseController` | `@Profile("database-v2")` | ✅ DONE |
| Treatment Records | `DatabaseV2TreatmentController` | `@Profile("database-v2")` | ✅ DONE |
| Commission Schemes & Closing | `DatabaseV2CommissionController` | `@Profile("database-v2")` | ✅ DONE |
| Audit Logs | `DatabaseV2AuditController` | `@Profile("database-v2")` | ✅ DONE |
| Reports & Dashboard | `DatabaseV2ReportController` | `@Profile("database-v2")` | ✅ DONE |
| Bootstrap Runner | `DatabaseV2BootstrapRunner` | Opt-in (`APP_BOOTSTRAP_ENABLED=true`) | ✅ DONE |

### Frontend — Cypress 9 specs / 13 tests PASS (0 failures)

| Spec | Description | Result |
|---|---|---|
| `auth-flow.cy.js` | Login → JWT → protected routes | ✅ PASS |
| `auth-guard.cy.js` | Unauthenticated redirect | ✅ PASS |
| `backend-health.cy.js` | `/actuator/health` liveness | ✅ PASS |
| `patient-lifecycle.cy.js` | Register → search → HN lookup | ✅ PASS |
| `appointment-lifecycle.cy.js` | Book → check-in → complete | ✅ PASS |
| `sales-receipt-lifecycle.cy.js` | Sale → receipt → reversal | ✅ PASS |
| `course-lifecycle.cy.js` | Purchase → consume session | ✅ PASS |
| `commission-lifecycle.cy.js` | Scheme → tiers → closing report | ✅ PASS |
| `reports-dashboard.cy.js` | Sales & appointment reports | ✅ PASS |

### Frontend static quality

| Check | Result |
|---|---|
| `npm run lint` (oxlint + eslint) | ✅ PASS |
| `npm run type-check` (vue-tsc) | ✅ PASS |
| `npm run test:unit` (vitest) | ✅ PASS (2 tests) |
| `npm run build` (vite) | ✅ PASS |

### CI Workflows

| Workflow | Status |
|---|---|
| `backend-verify.yml` — `mvn verify` | ✅ Configured |
| `frontend-verify.yml` — lint, build, Cypress | ✅ Configured (`APP_BOOTSTRAP_ENABLED=true`) |
| `compose-verify.yml` — docker compose build | ✅ Configured (`APP_BOOTSTRAP_ENABLED=true`) |

---

## Constraints Upheld

- ✅ Never altered `V1__initial_schema.sql`
- ✅ No compatibility views created
- ✅ No mock or fixture data used
- ✅ No tests skipped or disabled
- ✅ No hardcoded credentials or tokens
- ✅ All new controllers use `@Profile("database-v2")`
- ✅ Legacy controllers use `@Profile("!database-v2")`
- ✅ Cypress specs strictly under `frontend/cypress/specs/`
- ✅ Bootstrap runner opt-in via `APP_BOOTSTRAP_ENABLED=true`

---

## Safe Bootstrap

The schema contains no hardcoded demo accounts or secrets. On first start, set
`APP_BOOTSTRAP_ENABLED=true` in the environment. The `DatabaseV2BootstrapRunner`
will idempotently create the initial branch, staff, roles, and admin user.
Passwords and JWT/database secrets must never be committed to version control.
