# October 2026 requirements: what was built

Source: the handwritten Manager notes (appointments, course extension, commission, transactions) and the Figma review
of 15 October 2026. Each row says what was done, where, and what is still open.

## Migrations added (use V1007 or later for anything new)

| Migration | What it adds |
|---|---|
| `V1004__branch_booking_settings_service_duration.sql` | `branch.branch_email`, `license_no`, `booking_open_time`, `booking_close_time`, `default_appointment_minutes`; `service.default_appointment_minutes`. All optional |
| `V1005__course_extension_request.sql` | table `course_extension_request` (the owner asks, a Manager approves) |
| `V1006__payment_date_and_reference.sql` | `staff_monthly_payment.paid_date`, `payment_reference`; `monthly_commission_closing.reopen_reason`, `reopened_by`, `reopened_at` |

`RuntimeCheck` now expects 10 migrations. The Data Dictionary has none of these columns or the new table yet; they need
to be added there (the comparison in `commission-schema-migration.md` lists 52 tables, this makes 53).

## Appointments

| # | Requirement | Status |
|---|---|---|
| 1.1 | New-patient form: nickname, phone, date/time and therapist required; first/last name not asked | Done. The form has one name field, relabelled "ชื่อเล่น" (stored in `appointment.contact_name`) |
| 1.2 | Choose the therapist | Done, required |
| 1.3 | Detect free times | Done: `GET /api/appointments/availability` (branch booking window, past times, overlap with the therapist's other appointments). **Not done:** a Busy event added by hand in Google Calendar is not read back, because the sync only follows events it created itself |
| 1.4 | Book without choosing a service | Done (`service` optional for a registered patient, "ยังไม่ระบุบริการ") |
| 1.5-1.7 | Confirmed CURRENT patient goes to the services page; NEW registers then goes to services without searching again | Done. The first part already existed; NEW now goes to `/staff/services?patientId=` straight after registering |
| 1.8 | Do not link the registered patient back to the NEW appointment | Done: the link call was removed. The NEW appointment stays NEW and Confirmed (see open questions) |
| 1.9 | Duplicate-patient check by national ID / HN | Already existed |

Overlap rule: an appointment lasts the service's own length, else the branch default. When neither is set the old rule
stays (only an identical start time collides), so nothing changes until a Manager sets a length.

## Course extension (new)

`POST /api/course-extensions` (owner asks, any staff of the branch), `POST /{id}/approve` and `/{id}/reject` (Manager
only), `GET /api/course-extensions?courseId=` and `?status=Pending` (Manager). Approving writes the existing
`course_extension` row and moves `course.expiry_date`, which every holder of the course shares. A patient the course was
only shared with cannot ask (403). An expired course is extended from today. One open request per course.
The course page shows the request form, the waiting request and the real history (the invented "80 days" row is gone).

## Commission

| # | Requirement | Status |
|---|---|---|
| 3.1 | New tiers only after the current cycle is closed, used from the next cycle | Done in `CommissionSchemeService`: a new version that takes over a live scheme must start on the 1st of a month after the current one and only when no live course up to the current month is still unlocked (409 otherwise). The tier page says so and defaults to the first day of next month |
| 3.3 | Payment date and reference | Done: `POST /payments/{id}/pay` takes `{paidDate, reference}` (date defaults to today, never in the future) |
| 3.4, 3.5 | +/- adjustment applied in the payment, fewer steps | Done in the UI: "บันทึกและนำไปใช้ในงวด" creates, approves and applies in one action (a deduction needs the consent reference). The backend rules (cap at earnings, carry the rest over) are unchanged |
| 3.6 | Reopen keeps its reason | Done: `reopen_reason`, `reopened_by`, `reopened_at` on the closing (the audit log keeps every one) |
| 3.7, 3.8 | Rates and treatment fees on separate pages, no single-visit option in the course commission page | Already the case |

## Services, courses, settings

| # | Requirement | Status |
|---|---|---|
| 4.1 | Single Visit: 1 session, no expiry | Done in the form (no sessions or expiry fields) |
| 4.3 | "จำนวนครั้งรวม" for a course | Done |
| 4.4 | Course validity in days, months or years | Done (`validityValue` + `validityUnit`; `validityDays` still accepted) |
| 4.5 | Optional appointment length per service | Done (services and course templates) |
| 6.3 | Branch email, licence number, booking window, default appointment length | Done in settings and the API |

## Transactions and Calendar

| # | Requirement | Status |
|---|---|---|
| 5.1 | One list for sales and course usage with a type filter | Done: tabs are "รายการทั้งหมด" and "ประวัติการยกเลิก"; type filter: course sale, single-visit sale, course usage. The list now says what each line sold (`itemType`, `itemDescription` in `/api/payments`) |
| 5.3 | Another branch's transaction is read-only for a therapist | Done in the UI and enforced by the API (403 on cancelling a sale or a course of another branch) |
| 6.1, 6.2 | Manager connects the clinic account and sets each branch's calendar; a therapist only looks | The setup was already Manager-only. A therapist can now read the status of their own branch and open its calendar (`GET /api/calendar/status` returns only their branch) |

## Open questions for the Manager

1. NEW appointment after registration: it is left as it was (NEW, Confirmed). Should something mark it done?
2. Notes 4.2 ("bonus sessions ... gone") and 3.2 ("Commission pool = whole shop's sales") were not clear enough to change anything.
3. Should a Busy event typed by hand into Google Calendar block a free time? It needs a read of the calendar's events at booking time.
4. Phase 2: printed commission report for everyone.
