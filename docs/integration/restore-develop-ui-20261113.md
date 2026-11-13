# Restore develop UI after the Phase 2 merge revert

## Source and scope

`dc7e9242cf3d174b93e5dcefccc27d58fe766b64` reverted the merge at
`ca418de8cab23f0abb51bf68914489a6d469339b`. The resulting frontend tree
matches the pre-merge `b622a7aa7a4cf5b2dc9eb72e5a04fb515508c128` tree.

This change starts from `dc7e9242` and restores `frontend/src/` from
`ca418de8`. That includes the shared theme and fonts, manager-specific
screens, branch selection, appointment workspace, patient registration,
checkout, course collections and printing, reports, settings, and their
existing unit tests.

The restored UI also requires the corresponding application contracts.
The backend application code, migration additions, configuration, and
existing tests are restored from the same snapshot. Examples include:

| UI workflow | Required backend contract |
| --- | --- |
| Branch selection and role-specific screens | Branch response normalization, branch read/write access, authenticated user and staff metadata |
| Appointment workspace | Availability, progress, patient linking, check-in and treatment lifecycle |
| Course checkout and collections | Course payment collection, payment labels, receipt documents and cancellation rules |
| Course usage and printing | Batch usage, usage document snapshots, print revisions and extensions |
| Detailed reports | `/api/reports/sales-detail` and `/api/reports/payments-detail` |
| Reader setup and patient registration | Workstation management and ID-card read APIs |
| Manager compensation screens | Branch-scoped closings, adjustment lifecycle, commission rules and manual single-visit fee backfill |

The frontend dependency lockfile and build configuration remain on the
develop baseline. The restored source uses dependencies already declared
there. Backend test dependencies and coverage configuration come from the
source snapshot so its existing tests can compile and run.

This is an application restoration, not a revert of the entire revert.
Jenkins, GitHub Actions, Kubernetes manifests, deployment scripts, Ops
automatic migration/seed entry points, historical archives, and the
separate ID-card agent are outside this change.

The only Compose wiring addition passes the restored manager bootstrap
email setting through to the backend; `.env.example` documents the field.

## Running the restored application

The restored backend requires `JWT_SECRET`. Bootstrap provisioning also
accepts `CLINIC_BOOTSTRAP_MANAGER_EMAIL` alongside the existing manager
username and password settings. Use a disposable database to validate the
new migrations before upgrading an existing environment. The migrations
include payment/commission structures, required email and appointment
fields, course payment collections, calendar credentials, patient consent
fields, and reader registration.

The ID-card screens require the separately installed reader agent and
hardware for a real card read. No physical-reader or live Google Calendar
validation is claimed by this restoration.

## Validation

- Frontend production build and Vue/TypeScript check: passed.
- Frontend Vitest: 56 files / 314 tests passed using Node 24.15.0,
  `vitest run --pool=threads --maxWorkers=2`. Earlier attempts with the
  installed Node 24.13.0 stalled during DOM test initialization and were
  stopped; they are not counted as passing runs.
- Frontend source comparison with `ca418de8`: identical.
- Backend `mvn -B -DskipTests package`: passed, including test compilation.
- Full backend test results are recorded in the pull request.
- No application deployment or update to an existing application database
  was performed. Backend HTTP tests use disposable Testcontainers databases.
