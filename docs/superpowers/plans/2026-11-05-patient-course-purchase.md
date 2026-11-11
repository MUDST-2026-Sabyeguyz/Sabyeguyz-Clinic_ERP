# Patient Service and Course Purchase Flow Plan

**Goal:** From patient history, allow a patient to buy courses or single-visit services and retain any unused single-visit services for a later appointment.

**Architecture:** Reuse `SaleCheckoutView.vue` and its existing payment and receipt flow. Add backend persistence for prepaid single-visit service balances, since the current API requires a visit for every single-visit sale; consume those balances when the patient receives the service at a later appointment. Keep course entitlement use in the appointment based flow.

**Tech Stack:** Vue 3, Vue Router, TypeScript, existing course and catalog APIs.

**Spec:** User clarification in this conversation: clicking purchase from patient history should allow purchase of both courses and single-visit services; unused single-visit services must remain available for a later visit. Existing course entitlements still require an appointment to use.

## Scope

- Change both patient history purchase links to open the checkout route directly with the patient ID, without requiring an appointment.
- Allow selecting both saleable courses and single-visit services in this checkout context. Keep existing discounts, payment confirmation, and receipt behavior.
- Add a forward-only Flyway migration and backend API behavior that records a paid single-visit service as an unused entitlement when no appointment is attached. Preserve the existing behavior of recording a service delivered during an appointment.
- Show the patient's remaining prepaid single-visit services in the patient history and/or appointment service flow, and consume one entitlement when the service is delivered. Prevent consuming more than the available balance and preserve cancellation/reversal history.
- Keep “use existing course entitlement” separate; it remains available through the appointment flow.
- Update the history action label to “ซื้อบริการ / คอร์ส”.

## Files

- Modify `frontend/src/features/patients/views/PatientProfileView.vue` — direct purchase destination and label.
- Modify `frontend/src/features/patients/views/ManagerPatientProfileView.vue` — same destination and label for manager view.
- Modify `frontend/src/features/services/views/SaleCheckoutView.vue` — allow course and single-visit purchases without an appointment.
- Modify backend `SaleController` and sale/visit services — distinguish prepaid single-visit purchase from delivered service.
- Add a migration and persistence model/repository/service for prepaid single-visit balances and their redemption history.
- Update patient history and appointment service views to display and use the balance.

## Review Focus

- Opening from patient history preloads the correct patient and offers courses and single-visit services for sale.
- Purchasing a single-visit service without an appointment records one unused balance and does not create a treatment visit prematurely.
- Using a prepaid service at an appointment reduces the balance exactly once; duplicate requests and reversals preserve ledger consistency.
- Opening checkout from an appointment continues to record delivered services against that appointment.
- Existing course entitlement use remains unavailable from the no-appointment purchase route.
