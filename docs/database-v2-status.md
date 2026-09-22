# Database v2 integration status

The `database-v2` profile is the Compose runtime profile. Flyway applies only
`db/migration_new/V1__initial_schema.sql`, and Hibernate validation is disabled
because the legacy JPA entities do not yet map the confirmed schema.

## Verified v2 slices

- `/actuator/health` reports the expected v2 table set when PostgreSQL is available.
- `/api/auth/login` reads `app_user`, `user_role`, and `role` through the v2 JDBC adapter.
- `/api/branches` reads and writes the confirmed `branch` table through the v2 JDBC adapter.

## Remaining migration work

The legacy controllers still point at legacy table names and must not be treated
as v2-ready just because the application starts. The following feature groups
still need v2 repositories/DTOs and PostgreSQL tests before they can be enabled
against the new schema:

- patient registration/search/profile
- appointments and visit lifecycle
- service/course catalog and course ledger
- sales, receipts, payments, and reversals
- treatment records and commission reporting/closing
- staff, master data, rooms/resources, and permissions

Do not add compatibility views or silently ignore columns to make these paths
appear to work. The v2 schema has stricter foreign keys, enums, actor columns,
and audit fields; each adapter must persist those fields deliberately.

## Safe bootstrap requirement

The schema intentionally contains no hardcoded demo account or secret. A local
administrator must create the first `app_user`, role, staff, and branch records
using a secured environment-specific bootstrap process before login or writes
can be exercised. Passwords and JWT/database secrets must never be committed.
