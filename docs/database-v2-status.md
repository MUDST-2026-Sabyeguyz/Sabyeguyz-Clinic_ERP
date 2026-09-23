# Database v2 Integration Status

> **Last updated:** 2026-09-23 — Branch `xan`

The `database-v2` Spring profile is the Compose runtime profile. Flyway applies
only `db/migration_new/V1__initial_schema.sql`. Legacy JPA entities are isolated
under the `!database-v2` profile and must never be used by v2 controllers.

---

## Integration Status

Database v2 routes are implemented on `xan`. Direct backend tests are still
missing for branch, staff, master data, service/room, and payment-method APIs.
Local checks on a fresh Compose stack passed on 2026-09-23. GitHub Actions
has not yet verified this revision.

### Backend — 283 tests PASS (0 failures, 0 skipped; fresh PostgreSQL stack)

| Slice | Controller / Service | Profile annotation | Status |
|---|---|---|---|
| Auth & Login | `DatabaseV2AuthController` | `@Profile("database-v2")` | ✅ DONE |
| Branch Management | `DatabaseV2BranchController` | `@Profile("database-v2")` | ⚠️ Direct test pending |
| Staff & Therapists | `DatabaseV2StaffController` | `@Profile("database-v2")` | ⚠️ Direct test pending |
| Master Data & Lookups | `DatabaseV2MasterDataController` | `@Profile("database-v2")` | ⚠️ Direct test pending |
| Patient Registration & HN | `DatabaseV2PatientController` | `@Profile("database-v2")` | ✅ DONE |
| Appointments & Visit Lifecycle | `DatabaseV2AppointmentController` | `@Profile("database-v2")` | ✅ DONE |
| Sales, Receipts & Payments | `DatabaseV2SaleController` | `@Profile("database-v2")` | ✅ DONE |
| Course Ledger & Transfers | `DatabaseV2CourseController` | `@Profile("database-v2")` | ✅ DONE |
| Treatment Records | `DatabaseV2TreatmentController` | `@Profile("database-v2")` | ✅ DONE |
| Commission Schemes & Closing | `DatabaseV2CommissionController` | `@Profile("database-v2")` | ✅ DONE |
| Audit Logs | `DatabaseV2AuditController` | `@Profile("database-v2")` | ✅ DONE |
| Reports & Dashboard | `DatabaseV2ReportController` | `@Profile("database-v2")` | ✅ DONE |
| Bootstrap Runner | `DatabaseV2BootstrapRunner` | Opt-in (`APP_BOOTSTRAP_ENABLED=true`) | ✅ DONE |

### Frontend — Cypress 9 specs / 13 tests PASS (0 failures; fresh Compose stack)

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
| `backend-verify.yml` — PostgreSQL + `mvn verify` | Configured; remote run pending |
| `frontend-verify.yml` — lint, build, Cypress | Configured; remote run pending |
| `compose-verify.yml` — docker compose build | ✅ Configured (bootstrap disabled by default) |

---

## Constraints Upheld

- ✅ Never altered `V1__initial_schema.sql`
- ✅ No compatibility views created
- ✅ No mock or fixture data used
- ✅ Fresh local PostgreSQL run skipped 0 tests
- ✅ Runtime admin password is supplied by `APP_BOOTSTRAP_ADMIN_PASSWORD`
- ✅ All new controllers use `@Profile("database-v2")`
- ✅ Legacy controllers use `@Profile("!database-v2")`
- ✅ Cypress specs strictly under `frontend/cypress/specs/`
- ✅ Bootstrap runner disabled by default; enabling it requires an admin password

---

## Safe Bootstrap

On first start, set `APP_BOOTSTRAP_ENABLED=true` and a unique
`APP_BOOTSTRAP_ADMIN_PASSWORD` in the environment. The runner creates initial
branch, staff, roles, and admin user. If that admin already exists, its password
is updated to match the supplied secret. Disable bootstrap after initial setup.
Database, JWT, and bootstrap passwords must never be committed to version control.
