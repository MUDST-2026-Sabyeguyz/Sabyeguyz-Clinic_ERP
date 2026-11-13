# Therapist E2E results — 17 October 2026 (Asia/Bangkok)

Final full run: **93 tests / 82 passed / 11 failed / 0 skipped**, Chrome 155 headless, Cypress 16.1.0. Spec execution about 2 minutes. Backend v2,cypress-v2 with disposable PostgreSQL16 patients_bcc on port15434. Exit code1 correctly indicates failures.

This is functional coverage of the agreed Therapist feature inventory, not 100% code/path/combination coverage. Previous Patients BCC 26-case results are separate and were not rerun in this task.

| Suite | Tests | Passed | Failed | JUnit |
|---|---:|---:|---:|---|
| Therapist appointments — real backend | 12 | 11 | 1 | therapist-8d6d372542a81065e2046162448acef7.xml |
| Therapist authentication and access boundaries | 37 | 35 | 2 | therapist-096fcda1c1cb42fd1019fc3c1dad7b36.xml |
| Therapist compensation with actual earnings | 7 | 5 | 2 | therapist-dec2b05d8edcc129c3ecfefd2be5d3e1.xml |
| Therapist course entitlements | 10 | 8 | 2 | therapist-d938e65be1012a6a9128a49eaba3837b.xml |
| Therapist patient registration, profile and history | 15 | 14 | 1 | therapist-6370c536131e625dc5b3157761acac68.xml |
| Therapist services and receipts | 8 | 5 | 3 | therapist-0261dcf2b97905153185d7c38e5a343f.xml |
| Therapist transactions | 4 | 4 | 0 | therapist-dfd2499d730355a61444a26d95093ff9.xml |

## Confirmed failures and blocked continuations

| Cases | Observation | Consequence |
|---|---|---|
| AUTH-03 | Logout removes authToken but leaves authUser | Session cleanup assertion fails; the later return-to-staff assertion is not reached |
| AUTH-07 | Formula page API returns404 | Formula content cannot be verified |
| Appointment date-range | End-date popup year control clipped by ancestor/viewport at1440px | End-bound browser interaction blocked; no force-click workaround |
| Compensation commission/treatment detail (2 cases) | Nonzero earned totals but my-lines always returns empty list | Nonempty detail/source navigation blocked; missing-ID states independently pass |
| CRS-ROUTE-01 | Direct patient-scoped course URL shows another fixture patient | URL patient/course selection incorrect with multiple owners |
| CRS-GAP-01 | Transfer requires reason but no editable reason field exists | Browser transfer confirmation/persistence blocked; API transfer and balance conservation independently pass |
| PAT-05 unset referral | Profile PUT400: ไม่พบค่า หน้าสาขา ใน Referral Channel | Defaulted UI value is not a valid reference; explicit valid Friend fixture saves/reloads successfully |
| SVC-NOTE-01 | Actual request contains note but persisted receipt note is null | Browser-to-backend persistence mismatch |
| SVC-PAY-Cash | No cash choice rendered | UI/API coverage gap; cash UI availability expectation needs requirements confirmation |
| SVC-GAP-01 | No interactive course choice in checkout | UI/API coverage gap; API course checkout independently passes |

No production fixes, uncaught-exception suppression, core API mocks or forced hidden-control clicks were added. Print tests stub only window.print, not a real printer. Lifecycle operations absent in UI are labelled API-only. Main verification: 7spec full run, focused ESLint/Oxlint exit0, Maven test-compile/start success, reset opt-in omission rejected before tests, expiry helper compiled under target/test-classes and absent from target/classes.

## Case-by-case execution

IDs below identify rows in this snapshot. Explicit IDs in test names remain stable; unnumbered cases use their full descriptive test names. A failed case does not imply its downstream assertions executed.

| Row | Suite | Test case | Result |
|---|---|---|---|
| 1 | Therapist appointments — real backend | Therapist appointments — real backend creates a current-patient appointment from list, searches by HN and persists date/time/note | PASS |
| 2 | Therapist appointments — real backend | Therapist appointments — real backend requires a patient and a time, rejects unmatched patient search, and saves nothing | PASS |
| 3 | Therapist appointments — real backend | Therapist appointments — real backend edits a listed appointment and retains patient identity after rescheduling | PASS |
| 4 | Therapist appointments — real backend | Therapist appointments — real backend requires a cancellation reason and persists cancellation from the list | PASS |
| 5 | Therapist appointments — real backend | Therapist appointments — real backend opens occupied calendar slots for editing and filters the day by therapist | PASS |
| 6 | Therapist appointments — real backend | Therapist appointments — real backend creates from an empty day-calendar slot with date/time prefilled | PASS |
| 7 | Therapist appointments — real backend | Therapist appointments — real backend navigates weekly calendar forward and back while retaining therapist filtering | PASS |
| 8 | Therapist appointments — real backend | Therapist appointments — real backend filters appointment list using the real date-range controls | FAIL |
| 9 | Therapist appointments — real backend | Therapist appointments — real backend surfaces a real booking conflict and leaves the original appointment unchanged | PASS |
| 10 | Therapist appointments — real backend | Therapist appointments — real backend validates new-patient contact details before creating an unregistered appointment | PASS |
| 11 | Therapist appointments — real backend | Therapist appointments — real backend API-only: progresses check-in/start/complete and rejects editing a completed appointment | PASS |
| 12 | Therapist appointments — real backend | Therapist appointments — real backend API-only no-show action persists and renders cancelled calendar styling; terminal actions are rejected | PASS |
| 13 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-01 logs in through the real form and retains session on reload | PASS |
| 14 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-02 rejects a wrong password without creating a session | PASS |
| 15 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-03 logout removes all authorization state and prevents returning to staff pages | FAIL |
| 16 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-04 anonymous cannot access /staff/patients | PASS |
| 17 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-04 anonymous cannot access /staff/appointments | PASS |
| 18 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-04 anonymous cannot access /staff/appointments/calendar | PASS |
| 19 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-04 anonymous cannot access /staff/services | PASS |
| 20 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-04 anonymous cannot access /staff/courses | PASS |
| 21 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-04 anonymous cannot access /staff/transactions | PASS |
| 22 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-04 anonymous cannot access /staff/compensation | PASS |
| 23 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/select-branch | PASS |
| 24 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/dashboard | PASS |
| 25 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/reports | PASS |
| 26 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/reports/transaction/tier-round | PASS |
| 27 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/settings | PASS |
| 28 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission | PASS |
| 29 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/tiers | PASS |
| 30 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/closing | PASS |
| 31 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/compensation-settings | PASS |
| 32 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/courses/1 | PASS |
| 33 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/staff/1 | PASS |
| 34 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments | PASS |
| 35 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/new | PASS |
| 36 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/success | PASS |
| 37 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/schedule | PASS |
| 38 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/select-schedule | PASS |
| 39 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/closed | PASS |
| 40 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/finalized | PASS |
| 41 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/reopen | PASS |
| 42 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/reopen-success | PASS |
| 43 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/reopen-history | PASS |
| 44 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/review | PASS |
| 45 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1/payment-success | PASS |
| 46 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-05 therapist is redirected from /staff/commission/adjustments/1 | PASS |
| 47 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-06 backend independently rejects manager-only operations | PASS |
| 48 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-07 therapist can read compensation formula without manager controls | FAIL |
| 49 | Therapist authentication and access boundaries | Therapist authentication and access boundaries AUTH-08 legacy billing and login URLs resolve to their supported pages | PASS |
| 50 | Therapist compensation with actual earnings | Therapist compensation with actual earnings renders missing detail and source IDs safely without source actions or editable fields | PASS |
| 51 | Therapist compensation with actual earnings | Therapist compensation with actual earnings keeps unclosed course earnings unknown and displays the pending visit | PASS |
| 52 | Therapist compensation with actual earnings | Therapist compensation with actual earnings shows independently calculated totals, switches month and preserves tab selection | PASS |
| 53 | Therapist compensation with actual earnings | Therapist compensation with actual earnings opens a real commission line, calculation detail and source | FAIL |
| 54 | Therapist compensation with actual earnings | Therapist compensation with actual earnings opens a real treatment line, calculation detail and source | FAIL |
| 55 | Therapist compensation with actual earnings | Therapist compensation with actual earnings shows paid history and a later approved adjustment without changing the paid amount | PASS |
| 56 | Therapist compensation with actual earnings | Therapist compensation with actual earnings isolates another therapist from existing paid earnings and rejects manager payroll mutations | PASS |
| 57 | Therapist course entitlements | Therapist course entitlements CRS-ROUTE-01 patient-scoped history selects its route patient and course among multiple owners | FAIL |
| 58 | Therapist course entitlements | Therapist course entitlements uses one visit in the browser after confirmation and retains the usage in history | PASS |
| 59 | Therapist course entitlements | Therapist course entitlements API: transfers entitlement and conserves owner/recipient balance and audit history | PASS |
| 60 | Therapist course entitlements | Therapist course entitlements API: rejects zero, excessive and self transfers without changing either entitlement | PASS |
| 61 | Therapist course entitlements | Therapist course entitlements API and browser: a cancelled course cannot be consumed and remains in inactive history | PASS |
| 62 | Therapist course entitlements | Therapist course entitlements browser: validates transfer quantity and excludes the owner from recipient choices | PASS |
| 63 | Therapist course entitlements | Therapist course entitlements CRS-BOUNDARY-01 consumes the last entitlement and rejects a second usage without negative balance | PASS |
| 64 | Therapist course entitlements | Therapist course entitlements CRS-EXPIRY-01 rejects yesterday expiry and displays its inactive history | PASS |
| 65 | Therapist course entitlements | Therapist course entitlements CRS-EXPIRY-02 expiry today remains usable and the fixture endpoint rejects therapist writes | PASS |
| 66 | Therapist course entitlements | Therapist course entitlements CRS-GAP-01 browser allows entering the mandatory transfer reason | FAIL |
| 67 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-01 registers Thai patient through UI and persists identity and birth date | PASS |
| 68 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-02 required fields prevent an empty registration from creating records | PASS |
| 69 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-03 boundary/invalid xxx-xxx-xxxx: 123456789 | PASS |
| 70 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-03 boundary/invalid x-xxxx-xxxxx-xx-x: 123456789012 | PASS |
| 71 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-03 boundary/invalid example@gmail.com: invalid-email | PASS |
| 72 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-04 duplicate identity submission cannot create a second patient | PASS |
| 73 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-05 edits phone/address with unset referral and persists after reload | FAIL |
| 74 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-05 edits phone/address with valid referral and persists after reload | PASS |
| 75 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-06 cancelling edit leaves persisted data unchanged | PASS |
| 76 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-07 history has distinct empty payment and course states | PASS |
| 77 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-08 displays real paid receipt, filters history and previews/prints it | PASS |
| 78 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-09 invalid/missing profile not-a-number displays error | PASS |
| 79 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-09 invalid/missing profile 99999999 displays error | PASS |
| 80 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-10 rejects a future birth date before sending a registration | PASS |
| 81 | Therapist patient registration, profile and history | Therapist patient registration, profile and history PAT-11 cancelling registration confirmation preserves the form and creates nothing | PASS |
| 82 | Therapist services and receipts | Therapist services and receipts SVC-PAY-Cash sells a single visit and persists the matching payment and totals | FAIL |
| 83 | Therapist services and receipts | Therapist services and receipts SVC-PAY-BankTransfer sells a single visit and persists the matching payment and totals | PASS |
| 84 | Therapist services and receipts | Therapist services and receipts SVC-PAY-PromptPay sells a single visit and persists the matching payment and totals | PASS |
| 85 | Therapist services and receipts | Therapist services and receipts SVC-PAY-CreditCard sells a single visit and persists the matching payment and totals | PASS |
| 86 | Therapist services and receipts | Therapist services and receipts SVC-NOTE-01 retains the browser checkout note in the persisted receipt | FAIL |
| 87 | Therapist services and receipts | Therapist services and receipts API: sells a course and persists its entitlement and receipt | PASS |
| 88 | Therapist services and receipts | Therapist services and receipts API: rejects mixed payment methods without persisting a sale | PASS |
| 89 | Therapist services and receipts | Therapist services and receipts SVC-GAP-01 browser exposes a course selection control before course checkout | FAIL |
| 90 | Therapist transactions | Therapist transactions filters sales by receipt and patient, opens details and preserves a sale when backing out | PASS |
| 91 | Therapist transactions | Therapist transactions requires a reason and sufficient other-reason detail, confirms cancellation and persists audit history | PASS |
| 92 | Therapist transactions | Therapist transactions API: rejects blank cancellation reason and duplicate cancellation | PASS |
| 93 | Therapist transactions | Therapist transactions lists a real course usage under the usage tab and opens its details | PASS |
