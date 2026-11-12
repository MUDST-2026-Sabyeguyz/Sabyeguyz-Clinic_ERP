# Appointment process links

## Why this changed

Navigation is not evidence that registration, payment, or course redemption finished.
The appointment now reads committed document links. Browser back, refresh, and a
lost response must not create another bill or consume another course session.

## Changes

- `V1010__appointment_process_links.sql` adds one process row per appointment, with
  unique receipt/usage links. It preserves old documents without guessing matches.
- Payment and course endpoints lock the appointment before committing their
  financial record and link in the same transaction. Identical payment retries
  return the existing bill; different payloads require review.
- Course retries return the existing usage without decrementing rights again.
  Reversed usage is shown for review, rather than silently consuming another right.
- Progress reads live receipt totals, payments, refunds and usage status. It does
  not store a separate “paid” or “used” boolean that could become stale.
- Remaining payment is collected against the existing receipt. Under the same
  appointment/receipt locks, a repeated full-balance request cannot collect twice.
- Registration linking is idempotent for the same patient and rejects a different
  patient. Leaving the registration page prevents a late response from redirecting
  the user; the next entry reloads the saved state.
- Appointment prepayment no longer creates a completed walk-in. The service sale
  is attached to actual appointment attendance at check-in; treatment fees are
  accrued after real completion and full payment.

## Historical appointments

All appointments present when V1010 runs start in `REVIEW_REQUIRED`. A Manager can
inspect patient/branch document candidates, explicitly link the correct receipt
and/or course usage, or confirm with a reason that no earlier document exists.
This decision is audited. A document cannot be linked to two appointments.

## Operational note

The backend must be restarted/deployed with V1010 before the new progress UI can
operate. Compilation does not execute the migration or prove concurrency behavior
against a running database. No existing migration is modified.
