# Latest v2 database runtime

User decision: compare feat/database with feat/backend-v2-integration and switch the backend/frontend runtime to the latest v2 database.

Use feat/database commit 7172e9a V1__initial_schema.sql (2026-10-12) as the authoritative schema. Integration commit d66f29d (2026-10-12) supplies the newer v2 backend, but its SQL snapshot dates to 2026-10-08 and its default runtime remains legacy.

Keep the latest SQL unchanged unless a verified SQL defect prevents migration. Scan only clinicv2 entities/controllers for the default application, use PostgreSQL for v2 validation, and preserve the old API test suite in an explicit legacy test profile. The normal Compose database uses a separate v2 volume to avoid applying replacement V1 to existing legacy data. Verify migrations, Hibernate validation, real HTTP authentication, frontend build/unit tests and browser tests. No existing user database is reset.
