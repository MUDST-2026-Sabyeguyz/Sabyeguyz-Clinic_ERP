# Backend tests — Cypress only

Run from `backend` with Java 17+ (CI uses 21), Maven, and Node.js 24:

```sh
npm ci
npx cypress install
npm test
```

`npm test` packages the production jar, then runs every backend test in Cypress
against a real Spring Boot HTTP server. JUnit, Mockito, and Java test assertions
have been removed. Java remains necessary to compile and run the backend and its
test-only fixture helpers. No frontend, Docker, or running development database
is required.

```sh
npm run build
npm run test:api
npm run test:api -- --spec cypress/e2e/patients.cy.js
npm run test:api:open
```

On Windows, `./run-tests.ps1` builds and runs the entire Cypress suite;
`-Suite api` runs Cypress without packaging first. Additional Maven arguments
apply only to the build. `mvn package`/`mvn verify` compile/package Java; they do
not run the Cypress suite. Use `npm test` as the backend quality gate.

## Isolation and lifecycle

The Node runner starts Spring Boot with the `cypress-api` Maven profile, waits
for Spring Boot's startup check, runs Cypress, then stops the JVM even if tests
fail. Close the Cypress window to stop the interactive server. Ports 18080
(HTTP, loopback only) and 19001 (Spring Boot shutdown/JMX) must be free; the runner
refuses to touch an existing process on either port. After an interrupted run,
stop the test server with `mvn -Pcypress-api spring-boot:stop` before restarting.

The `cypress` Spring profile uses its own in-memory H2 database. It does **not**
activate the `test` profile, so normal JWT authentication and method permissions
remain enabled. Before every test, Cypress logs in with the seeded test manager
and resets mutable fixture data. Fixtures include a 9,000-baht course with five
paid sessions plus one bonus session. Report fixtures move sale/purchase dates
to 2032-07-04, preserving the existing deterministic report assertions.

All Java helpers and the profile are exclusively in `src/test`; they are not
packaged in the production jar. Startup requires the dedicated H2 URL and helper
endpoints require manager authorization. They expose fixture operations and raw
production results (for example the package-private HN formatter and repository
overlap query), never test assertions or pass/fail verdicts. The Cypress config
rejects a different base URL. Specs must run serially against this fixture
server because every test resets the shared fixture database.

Concurrency scenarios use `cy.parallelApi`, backed by a Node `cy.task` that
releases two independent HTTP requests together. They run in separate Spring
transactions and Cypress checks both outcomes and persisted state. Ordinary
queued `cy.request` calls would be serial and are not used for these races. The
task settles both requests before returning, including when one fails.

Like the previous Java integration suite, this suite uses Flyway through V20
and Hibernate schema update. V21+ demo migrations contain PostgreSQL-specific
SQL, and H2 does not enforce PostgreSQL's exclusion constraint for overlapping
appointments. These tests do not replace migration/constraint verification on
PostgreSQL. The concurrency scenarios exercise competing HTTP/service
transactions on H2.

## Migration map

| Previous MockMvc class | Cypress spec | Cases preserved |
| --- | --- | ---: |
| HelloControllerTest | contracts.cy.js | 1 |
| PatientControllerTest | patients.cy.js | 2 |
| PatientQueryApiTest | patients.cy.js | 9 (pagination errors split into 2 tests) |
| PatientOpenApiTest | contracts.cy.js | 1 |
| AppointmentApiTest | appointments.cy.js + contracts.cy.js | 11 |
| AppointmentLifecycleApiTest | appointments.cy.js | 15 |
| AppointmentQueryServiceTest (MockMvc methods only) | appointment-queries.cy.js | 5 |
| CourseLedgerApiTest | courses.cy.js | 9 |
| SaleApiTest | sales.cy.js | 12 |
| ReportApiTest | reports.cy.js | 10 |

The 75 original API cases are represented by 76 Cypress cases (pagination
validation split into two), plus five authentication/authorization cases.
The remaining 146 original Java cases are migrated as follows:

| Previous classes | Cypress spec | Cases |
| --- | --- | ---: |
| AppointmentState/BookingService/RescheduleService/QueryService/RepositoryTest | appointment-rules.cy.js | 58 |
| SaleServiceTest | sale-rules.cy.js | 24 |
| CourseLedgerServiceTest | course-rules.cy.js | 15 |
| ReportServiceTest | report-rules.cy.js | 29 |
| AuthService/PatientRegistrationService/HnGenerator/CourseRepositoryTest | identity-rules.cy.js | 11 |
| Appointment/CourseLedger/SaleConcurrencyTest | concurrency.cy.js | 9 |

Total: **227 Cypress cases**, preserving the original 221 scenarios plus six
additional cases. All assertions are JavaScript/Chai. Tests of internal Java
methods use focused helper endpoints; most cases exercise the public API and
therefore now verify HTTP integration rather than isolated Java units.

Per-method mapping: [appointments](migration/appointments.md),
[financial](migration/financial.md), [reports](migration/reports.md),
[identity and concurrency](migration/identity-and-concurrency.md).
[Original Java case inventory](migration/original-java-cases.json) records the
146 methods migrated in the final phase. Frontend tests and CI are independent.

CI runs `npm test` and uploads Cypress XML and failure screenshots. XML uses the
JUnit report format for CI compatibility; this does not invoke JUnit. Cypress
reports are written to `cypress/results/api-[hash].xml`, with old reports cleared
before each run.

Reference: [Cypress API testing](https://docs.cypress.io/app/guides/api-testing).
