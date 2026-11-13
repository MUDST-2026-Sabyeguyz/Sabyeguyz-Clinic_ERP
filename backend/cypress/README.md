# Backend tests on the current v2 schema

Requirements: Java 17+ (CI uses 21), Maven, Node.js and a dedicated disposable
PostgreSQL database. The suite resets fixture data; never use a working database.

From `backend` (PowerShell):

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:5432/clinic_v2_test'
$env:DATABASE_USERNAME='clinic'
$env:DATABASE_PASSWORD='<test database password>'
npm ci
npx cypress install
npm test
```

`npm test` runs `mvn clean package` (including JUnit tests), then Cypress against
ClinicV2Application. JUnit database tests may also require Docker/Testcontainers.
`npm run test:api` / `npm run test:v2` run only Cypress; `npm run test:api:open`
opens Cypress. Select a spec with `npm run test:api -- --spec cypress/e2e-v2/patients.cy.js`.
`run-tests.ps1` uses these same commands.

The runner uses Maven profile `cypress-v2` and Spring profiles `v2,cypress-v2`,
applying `db/migration` plus test-only `db/seed_v2`. It uses loopback port 18080
and JMX port 19001, refuses occupied ports, and stops its JVM after the run.
After an interrupted run: `mvn -Pcypress-v2 spring-boot:stop`.

Specs are in `cypress/e2e-v2`; fixtures/controllers are test-only. V2 service
scenario bridges use repository doubles and do not create a legacy database.
The former legacy H2 launcher and its API specs have been retired. Historical
coverage reports describe the old suite and do not certify current v2 coverage.
