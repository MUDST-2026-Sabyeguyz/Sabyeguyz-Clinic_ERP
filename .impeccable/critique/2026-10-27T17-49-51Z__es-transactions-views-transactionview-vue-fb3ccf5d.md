---
target: /staff/transactions cancellation detail
total_score: 26
max_score: 40
na_heuristics:
p0_count: 0
p1_count: 2
target_identity: "file:D:\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\transactions\\views\\TransactionView.vue"
target_fingerprint: "sha256:7758ef924931acb19a2efc341c14f4023984c17abd3b41289279d59cd25b6175"
target_path: "D:\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\transactions\\views\\TransactionView.vue"
timestamp: 2026-10-27T17-49-51Z
slug: es-transactions-views-transactionview-vue-fb3ccf5d
---
## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|---|---:|---|
| 1 | Visibility of System Status | 3 | Status, disabled submit, loading, and success states are clear; audit actor is absent. |
| 2 | Match System / Real World | 3 | Clinical and financial terms, HN, service, amount, and reasons fit staff vocabulary. |
| 3 | User Control and Freedom | 3 | Back, edit, cancel, and Escape are present; recovery after a completed void is unclear. |
| 4 | Consistency and Standards | 3 | Teal cards, controls, labels, and dialogs are coherent. |
| 5 | Error Prevention | 3 | Required reason and explicit confirmation reduce accidental cancellation; downstream effects are not stated. |
| 6 | Recognition Rather Than Recall | 3 | Core record evidence is visible but the amount is separated from the action flow. |
| 7 | Flexibility and Efficiency | 2 | No visible accelerator or compact reconciliation path for experienced staff. |
| 8 | Aesthetic and Minimalist Design | 3 | Restrained and legible, but duplicate action entry and empty fields consume attention. |
| 9 | Error Recovery | 2 | Submission errors are announced but correction after completion is not explained. |
| 10 | Help and Documentation | 1 | No contextual policy or impact guidance for a void. |
| **Total** | | **26/40** | **Acceptable; significant improvement needed** |

## Design Specificity Verdict

The Thai clinical context, HN, service/course model, and void rationale are product-specific. The composition is still a generic record-card-and-form flow rather than an audit-grade cancellation surface. The deterministic scan returned zero findings for `TransactionView.vue`; this does not identify visual or workflow risks.

## Overall Impression

The page is calm, legible, and has a sensible confirmation step. Its main gap is trust: it asks staff to make an irreversible financial decision without summarizing downstream effects or preserving visible audit accountability.

## What's Working

- Mandatory, human-readable reason choices reduce free-text ambiguity.
- The confirmation dialog repeats patient, service, amount, reason, and note before commit.
- Restrained color separates normal workflow from irreversible confirmation.

## Priority Issues

- **[P1] Audit credibility breaks on the core record.** Payment method, phone, and cancellation actor render as `—`. Hide unavailable nonessential values; for audit-critical attribution, show authenticated actor, role, branch, timestamp, and reversal reference.
- **[P1] The irreversible consequence is under-explained.** The reason helper explains that history is updated, not what changes. Add a concise impact summary above the CTA and in the dialog, tailored to sale versus usage.
- **[P2] Detail and action hierarchy are fragmented.** A shortcut to specify a reason is separated from the actual action flow. Keep a compact, sticky transaction/impact summary beside the reason form or remove the duplicate shortcut.
- **[P2] Reason selection is cognitively dense.** Five flat radio choices include overlapping operational concepts. Group by outcome or add concise impact hints.
- **[P2] Recovery and policy guidance are missing after completion.** Success should include audit reference, actor, next safe action, and a correction/escalation route where reversals are irreversible.

## Persona Red Flags

**Alex (power user):** Must scan a broad record and five radio choices with no accelerator or compact reconciliation summary.

**Jordan (first-timer):** Cannot tell what a void changes for payments or course entitlements; the helper explains logging, not consequences.

**Sam (accessibility-dependent):** Radio focus has no authored state beyond browser defaults; status meaning relies partly on color and a scroll-dependent review path is difficult at 200% zoom.

## Minor Observations

- Validate intended density at 100% and 200% zoom; the reviewed browser was at 50%.
- Hide the phone row when absent rather than showing `เบอร์โทรศัพท์ -` prominently.
- Use tabular numerals for monetary values across detail, confirmation, and history.
- `ช่องทางชำระ —` needs an explicit operational explanation when payment handling is required.

## Questions to Consider

1. What evidence must staff see to defend this cancellation during an audit six months later?
2. If sale voiding and course-usage reversal have different downstream effects, why do they share the same decision language?
3. Can the interface state what will change before enabling the irreversible action?
