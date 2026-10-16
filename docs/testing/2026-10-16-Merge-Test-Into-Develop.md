# Merge test into develop — 2026-10-16

## Inputs and conflict policy

- Target baseline: develop at be87da4e334d7cacd7319aa8dd66aabb32af0393.
- Source: origin/test at 5f1cff2fc7ce0c87f2c2b135fd80bfd76c6ef2a3 (no local test branch exists).
- User instruction: merge test into develop; prioritize develop for conflicts on settings pages.
- Settings views/styles, CalendarSettingsView, CommissionTierView and CommissionCompensationSettingsView retain develop's contents.
- Other screens receive test's workflows, with API contract reconciliation. Existing regression tests are retained.

## Integration resolutions

- Preserve develop's full API client and add branch aliases required by test's screens while keeping branchCode, booking fields and settings mutation requests.
- Pass the selected branch through all patient search pages.
- Keep develop's full therapist compensation controller; remove automatically merged duplicate stub endpoints from test.
- Retain report request ordering and active master-record filters.
- Restore payroll payment, receipt and refund routes; keep the payroll payment tab available.
- Select commission scheme versions using effective dates (inclusive end date), and normalize numeric/string version matching.
- Honor closed staff, branch and payment-method settings in both sales views.
- Keep course usage reversal connected to the backend and propagate failures.
- Correct an existing quote syntax error and Cypress command-chain return in commission-cycle-rules.cy.js so its assertions can run.

## Verification

| Check | Result |
| --- | --- |
| Frontend build, including type-check | PASS |
| Backend Maven compile | PASS |
| Settings, commission rules/formula and API unit tests | 72/72 PASS |
| Added contract tests: dated/versioned rules, closed payment methods, usage reversal failure | 6/6 PASS within full run |
| Report lookup and delayed-response tests | 2/2 PASS |
| Backend commission-cycle-rules + commission-payroll on isolated PostgreSQL 16 | 24/24 PASS |
| Complete retained frontend unit suite | 126/156 PASS, 30 FAIL |

The complete frontend suite is **not green**. The failures below remain against the imported test screens. They include changed selectors/navigation and behavioral expectations; they have not all been established to be harmless test drift. No assertions were deleted or weakened to hide them. This merge is not evidence of full application acceptance or CI success.

Build emits a CSS import-order warning from the retained develop CommissionTierView; it does not fail compilation. Tests used a separate task-owned database on 127.0.0.1:55439; no live clinic records were changed. The restored refund route is reachable directly at /staff/refunds; test's main navigation does not link to it. No deployment or push was performed.

## Remaining frontend failures

### src/features/appointments/__tests__/AppointmentMerge.spec.ts

- keeps the server search during calendar polling and stops polling after unmount
- shows the actual off-grid time and phone together with the connected Google calendar link

### src/features/appointments/__tests__/AppointmentView.spec.ts

- shows the Figma patient details form when creating an appointment for a new patient
- places service type on the row below patient type for current and new patients
- keeps required markers beside new-patient field labels

### src/features/commission/__tests__/PayrollViews.spec.ts

- lists adjustments with a minus sign and what is left
- needs the consent reference before a deduction is approved
- refuses to save an incomplete adjustment without calling the server
- asks for the consent reference when a deduction is applied in the same step

### src/features/courses/__tests__/CourseActionView.spec.ts

- waits for initial course history before allowing the transfer form to open

### src/features/patients/__tests__/PatientHistoryPayment.spec.ts

- keeps receipt lifecycle separate from persisted payment label and omits unsupported credit

### src/features/patients/__tests__/PatientHistoryView.spec.ts

- requests receipt pages for one patient and shows whole net instead of paid

### src/features/services/__tests__/servicesView.spec.ts

- opens the persisted receipt automatically after successful payment
- keeps a successful POST locked when navigation fails and offers the saved receipt

### src/features/transactions/__tests__/TransactionView.spec.ts

- shows persisted FullyPaid independently of PAID lifecycle and fully paid amount
- shows persisted Installment independently of PAID lifecycle and fully paid amount
- shows persisted Deposit independently of PAID lifecycle and fully paid amount
- does not invent a payment label for legacy receipts without one
- displays QR as พร้อมเพย์
- displays QR_CODE as พร้อมเพย์
- displays PROMPTPAY as พร้อมเพย์
- displays PromptPay as พร้อมเพย์
- prevents entering internal notes that the reversal API cannot persist
- shows backend receipts from every page and resolves branchCode names
- has one list for sales and course usage, not separate tabs
- lets a therapist only read a transaction of another branch
- loads backend sale details instead of fabricated contact and quantity
- keeps sale reversal failure out of success and history
- keeps usage reversal failure out of success and history
- waits for backend confirmation, retains warning focus/Escape and records success
