# Database policy: current schema only

The application and supported test/CI launchers use `backend/src/main/resources/db/migration`.
Cypress runs ClinicV2Application against a dedicated PostgreSQL database using the
`v2,cypress-v2` profiles. Test-only synthetic data comes from `db/seed_v2`.

The legacy schema fixtures, importer, legacy H2 Cypress launcher/specs and legacy
migration workflow have been retired. No legacy database is created, renamed or
imported by the supported test commands. Historical code and SQL remain available
in Git history. Existing databases and backups are not modified by this change.

`npm test` builds/runs Maven tests and then the v2 Cypress API suite;
`npm run test:api` and `npm run test:v2` run the same v2 API runner.
See [backend test instructions](../backend/cypress/README.md).

V2 repository-double scenario tests previously hosted by the legacy launcher have
moved to the v2 test application and `cypress/e2e-v2`. Legacy API specs were retired;
their counts must not be reported as passing or assumed to have one-to-one v2 coverage.

If a deleted legacy workflow was a required branch-protection check, a repository
administrator must remove that requirement in GitHub before merging. This local
change does not alter repository protection settings.
