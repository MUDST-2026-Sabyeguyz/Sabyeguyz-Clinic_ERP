# Backend integration with the confirmed database schema

The database-team schema in
`backend/src/main/resources/db/migration_new/V1__initial_schema.sql` is the
source of truth for the `database-v2` profile.

## Run the schema bridge

Start PostgreSQL with an empty database, set `DATABASE_URL`,
`DATABASE_USERNAME`, and `DATABASE_PASSWORD`, then run:

```powershell
./mvnw spring-boot:run -Dspring-boot.run.profiles=database-v2
```

Flyway installs only the confirmed v2 baseline. The Actuator health response
at `GET /actuator/health` is `UP` only when all 40 expected base tables exist in
the active PostgreSQL schema.

## Current compatibility boundary

This profile proves the backend can connect to PostgreSQL and that Flyway
installed the confirmed schema. It does not claim that the legacy API model is
already compatible. The existing Java entities still target the previous
plural-table model and Hibernate schema validation is therefore disabled only
for this opt-in profile.

The backend migration should proceed by domain in this order:

1. Identity and access: `branch`, `staff`, `app_user`, roles and permissions.
2. Patient, service, room, appointment and visit.
3. Receipt, sales and payment transactions.
4. Customer-owned courses, course ledger, transfers and visit usage.
5. Commission closing, allocation and audit.

Do not enable `database-v2` in production until the endpoint paths used by that
deployment have been remapped and tested against PostgreSQL.

## Data Dictionary difference to resolve

The workbook linked from `origin/main:docs/database/README.md` contains 39
worksheets. The SQL baseline contains 40 tables because it also defines
`receipt`; there is no matching `Receipt` worksheet in the current workbook.
The SQL table is retained because the confirmed migration references it from
`course` and `sales_transaction`, but the database team should add it to the
dictionary.
