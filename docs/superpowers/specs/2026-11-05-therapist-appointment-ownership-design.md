# Therapist appointment ownership

## Intent

Allow a therapist to register a new patient and continue an appointment only when that appointment is assigned to the therapist in their own branch. Appointment confirmation remains tied to successfully recording service/payment or using course rights.

## Current cause

The login response already contains `staffId`, but both appointment views try to derive it from `/api/staff`. That endpoint intentionally omits `userId`, so the therapist-to-staff match always fails and the UI treats their appointments as unowned.

## Approach

- **Selected: use the authenticated session's `staffId`.** This is the smallest fix and keeps the current owner and branch checks intact.
- Add a new API endpoint to resolve the current therapist's staff record. This adds backend contract and maintenance for information already provided at login.
- Allow therapists to manage all appointments in their branch. This broadens access beyond the requested owner-only scope.

## Scope and constraints

Update both therapist and manager appointment views to read `staffId` from the authenticated user object. Keep existing backend authorization, branch scoping, patient registration, and service/payment/course confirmation flow unchanged. Do not change database data or appointment business rules.

## Success condition

The assigned therapist can register a NEW patient and proceed with their own appointment; an appointment assigned to another therapist remains read-only. Confirmation still occurs only after the existing service/payment or course-right flow succeeds.
