# Latest database activation check (15 Oct 2026)

Branch: `feat/backend-v2-integration` at `b49132b` before these changes. Latest `origin/feat/database`: `acc401f`.

## Findings and changes

- Active v2 Flyway location is `classpath:db/migration`. It contains V1–V4 and V1001–V1003. The old source copy under `db/pending_migrations` was removed after the adapted V1002 became active.
- A PostgreSQL database already at V1001 failed startup with `Detected resolved migration not applied to database: 4`. The v2 profile now permits out-of-order migration so V4 applies before V1002/V1003 on those databases. Fresh databases still apply all seven versions in version order.
- The V4 `app_user.email` column is now mapped and used by user create/update, API responses, and case-insensitive email login. Username login remains available. The service checks the new username/email rules before writing.

## Verification on disposable PostgreSQL 16

| Check | Result |
|---|---|
| `FlywayOutOfOrderUpgradeCheck`, database previously at V1001 | Passed; installed order V1001 → V4 → V1002 → V1003, Hibernate validate passed |
| `RuntimeCheck`, fresh database | `RUNTIME_CHECK_OK`; all seven migrations, user email create/update/persistence/login, and existing HTTP workflow |
| `SchemaCheck`, migrated database | `SCHEMA_CHECK_OK`; 551 mapped columns, 0 problems, 50 entities, 23 controllers, 40 services |
| `check-v1002.sh`, fresh/seeded/old-row scenarios including V4 | 15 passed, 0 failed |
| `npm run test:v2 -- --spec cypress/e2e-v2/auth.cy.js` | 6 passed, 0 failed |
| `npm run test:api -- --spec cypress/e2e/v2-core.cy.js` | 54 passed, 0 failed using the dedicated H2 in-memory fixture database |

The full v2 Cypress run completed `admin-misc.cy.js` (14/14) and then Electron stopped while opening `appointment-workflow.cy.js` with `ERR_NETWORK_CHANGED (-21)`. It did not produce a full-suite result.

V4 rejects pre-existing usernames containing `@`. Check and resolve such accounts before upgrading a live database; the migration does not rewrite usernames or invent email addresses.
