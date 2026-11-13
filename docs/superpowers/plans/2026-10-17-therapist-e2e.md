# Therapist E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Extend real-backend Cypress coverage across the Therapist workflows approved in chat.

**Architecture:** Dedicated config and shared helpers in frontend/cypress/support-therapist; independent domain specs in e2e-therapist. API prepares fixtures using manager, browser actions and API verification use therapist. Disposable PostgreSQL/cypress-v2 only; sequential execution to prevent reset races. No production behavior changes or suppression of application exceptions.

**Verification:** Run Chrome headless against localhost:5173 proxying to 127.0.0.1:18080; save JUnit and failure screenshots. Distinguish executed failures, blocked downstream paths, API-only checks and browser workflows in coverage report.

- [x] Shared helpers/config; auth, permissions and patient profile/registration/history tests.
- [x] Appointment list/calendar/create/edit/status transitions and validation tests.
- [x] Service sales, course entitlements/use/transfer and transaction tests.
- [x] Therapist compensation overview/detail/source/payments/adjustment tests.
- [x] Review each domain, run sequentially, correct test defects only, record application defects and traceable coverage/gaps.

## Shared helper contract

Import { api, visit, patient, fixtures, sale, book } from '../support-therapist/helpers'.
support file e2e.js resets before each test, saves fixtures via exported mutable object and authenticates manager + therapist + therapist2. api(method,path,body,role='therapist', options={}) uses direct isolated API with failOnStatusCode false; explicitly assert statuses. visit(path,role='therapist') sets actual API session. patient(overrides={}) creates valid Thai patient as manager, returns body. sale(patientId, overrides={}) creates 800 cash single service as therapist and asserts 201. book(patientId, overrides={}) creates tomorrow 09:00 appointment for branch01/therapist1/service60 and asserts201. No domain should reset or launch Cypress independently. Avoid speculative expected UI behavior: read source and use independently specified business expectations.

Ruling: Existing package.json/lock edits are user changes and must be preserved. Existing patients BCC config/spec remain intact; new suite supplements them. In-place work on existing feat/e2e-therapist checkout, no branch switch or shared database modification.

Ruling: Source-blocked UI workflows remain explicit failing regressions, with downstream paths labelled blocked. Appointment lifecycle operations absent in UI are API-only tests. Do not suppress uncaught errors, force-click clipped controls or weaken expectations to make application defects pass.

Ruling: Add manager-only expiry fixture under backend src/test/cypress-v2 so real PostgreSQL expiry boundaries can be exercised without changing production behavior. Verify compile location, endpoint403 for therapist and actual expiry/use results.

Review: Domain implementations reviewed against source by independent agents; fixed exact Thai confirmation selectors, asynchronous data-loading races, valid authorization payloads and JUnit409 duplicate contract based on runtime evidence. Final whole-suite review corrected three overbroad coverage statements (checkout consistency, lifecycle API-only, adjustment isolation not proven).
