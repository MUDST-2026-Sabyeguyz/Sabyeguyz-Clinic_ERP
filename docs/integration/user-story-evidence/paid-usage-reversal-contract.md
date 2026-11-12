# Chat4 contract — paid course-usage reversal (US097.02)

## Decision
A paid course-usage reversal must preserve the historical paid commission and treatment-fee ledger/payment. The correction must use an explicit, later-period negative `StaffPayAdjustment`; it must never rewrite the paid payout or silently debit an employee.

Chat2 should call a dedicated commission operation, proposed signature:

```java
CourseCommissionService.reverseUsageWithAdjustments(CourseVisitUsage usage, String reason, Long actorId)
```

The operation should run in the same transaction as restoring the course entitlement and changing usage state. It should not call the generic public `StaffPayAdjustmentService.create` twice from the controller: that API accepts arbitrary amounts and has no per-source paid-component amount guard or reversal-level idempotency.

## Required behavior
- Inspect the active `CommissionAllocationLedger` and the independent `ownerMonthlyPayment` / `treatingMonthlyPayment` statuses.
- Leave the original allocation and every Paid payment amount unchanged.
- For a paid owner component (`ownerNetCommission > 0`), create a negative adjustment for the owner; for a paid treating component (`treatmentFeeAmount > 0`), create one for the treating therapist. If both components belong to the same staff member, combine only after validating each component and keep the one source allocation/visit linked.
- Adjustments reference the allocation, course and visit; `originalMonth` is the service/visit month, `appliedMonth` is the next eligible month after the staff member's latest paid month, and the reason includes the approved reversal reason/source.
- New adjustment status is `Pending`, `processedAmount=0`, `remainingAmount=abs(amount)`. Do not approve it, create an application, or change a payment automatically. Manager must approve it through the existing adjustment workflow; applying a negative amount later still requires the separate consent/reference approval that `StaffPayAdjustmentService.approveApplication` enforces.
- For a component not yet Paid, append the normal negative allocation reversal for that component. If the other component is already Paid, the reversal row must not negate that paid amount; it must be zero for that component while the original paid amount remains intact.
- Update usage/entitlement and append correction records atomically. A rejected reversal must leave all rows and entitlements unchanged. Repeating or racing the same reversal must not create duplicate adjustments or ledger reversals.
- If the exact correction amount cannot be determined from the source allocation, reject for Manager review; never guess.

## Existing implementation fit
`StaffPayAdjustment` already links an allocation/course/visit and stores original/applied month, signed amount, reason, approval state, processed/remaining values. `StaffPayAdjustmentService.create` creates Pending and `approve` is separate; `createApplication` reserves on an Open later-month payment and deduction approval requires a reference. A dedicated operation is needed to derive the exact component amounts, handle mixed Paid/unpaid components, and keep the course reversal and pending adjustments atomic.

This contract does not change the normal refund path: refunding unused rights must not claw back earned treatment pay (US102). It applies to an explicitly confirmed erroneous usage reversal under US097.

## Regression cases to add
1. Owner Paid + treating unpaid: owner payment/allocation remains paid and unchanged; negative owner adjustment Pending; unpaid treatment component reversed only; rights restored once.
2. Owner unpaid + treating Paid: symmetric behavior.
3. Both Paid: two component amounts are represented without changing either prior payment; no applications/payment generated.
4. Neither Paid: existing reversal behavior, no pay adjustment.
5. Repeat/concurrent reversal: one usage reversal, one pending correction per staff/source, no duplicate restore.
6. Invalid reason/failed persistence: no partial adjustment, ledger or entitlement writes.

Chat2 owns the `CourseVisitUsageService` call site and integration tests. Chat4 owns the commission/pay-adjustment operation. Keep `097.02` failed until the integrated path and these tests pass.
