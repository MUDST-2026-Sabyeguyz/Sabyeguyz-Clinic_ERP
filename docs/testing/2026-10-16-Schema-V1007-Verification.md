# V1007 schema integration verification (16 Oct 2026)

The schema from `feat/database` commit `12adb20` was adapted as active
`backend/src/main/resources/db/migration/V1007__schema_and_google_calendar.sql`.
V1004 already owns `branch.license_no`, `branch.default_appointment_minutes`,
and its appointment-duration constraint, so V1007 does not recreate them.
V1007 copies existing branch email/hours and service duration to the new
dictionary columns. It translates existing patient titles to match the new
customer-type constraint.

## Disposable PostgreSQL verification

PostgreSQL 16.15 ran in the isolated container
`alice-schema-v1007-20261016` (`codex.task=schema-v1007-20261016`), bound
only to `127.0.0.1:55447`. Its databases were `clinic_v1007` for fresh
runtime/schema checks, `clinic_v1007_upgrade` for upgrade rehearsal, and
`clinic_v1007_api` for API regression. No existing database was reset.

- Fresh `RuntimeCheck`: **RUNTIME_CHECK_OK**. Flyway applied all 11 active
  migrations through V1007; production v2 startup, Hibernate validation, and
  HTTP smoke passed.
- `SchemaCheck` on that database: **SCHEMA_CHECK_OK**; 566 mapped columns,
  0 problems; Hibernate validated 50 entities and wired 24 controllers and
  41 services.
- Upgrade rehearsal: applied V1-V1006, inserted legacy settings and a foreign
  patient with a Thai title, then applied V1007. Flyway reported
  `MIGRATED=1`. Readback showed branch email/hours, service duration and
  title (`Foreigner`/`Mr.`) copied or converted as intended.
- Full backend v2 Cypress API run: 315/316 passed. The single failure was an
  existing receipt-history case expecting a sale with both a line and bill
  discount, which V1007 now prohibits. The case was changed to verify receipt
  totals with a line discount, and an explicit rejection case was added.
  Rerunning `receipt-history.cy.js` passed **10/10**. A final full run then
  passed **317/317 across all 32 specs**.
- `mvn test-compile` and `git diff --check` passed. Maven Surefire's `test`
  goal does not execute the project's Cypress/scenario suite.

Logs are in ignored `backend/target/schema-v1007-*.log`. The disposable
container remains available for inspection.

## Integration limits

The new `google_calendar_connection` and `calendar_busy_block` tables are
created but not used by the current service-account Calendar integration.
That connection/credential workflow requires a separate product decision.
Before merging `feat/database`, exclude its original V5 migration: applying
both V5 and V1007 would repeat the same schema changes. Before using V1007
on a real populated database, check historical email values and mixed line/
bill discounts against the new constraints; this rehearsal used a fixture,
not a real production snapshot.
