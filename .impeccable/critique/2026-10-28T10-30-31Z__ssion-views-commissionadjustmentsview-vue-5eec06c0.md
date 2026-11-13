---
target: /staff/commission/adjustments
total_score: 20
max_score: 40
na_heuristics:
p0_count: 0
p1_count: 2
target_identity: "file:D:\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\commission\\views\\CommissionAdjustmentsView.vue"
target_fingerprint: "sha256:a246da83648bb7c6dd664954b8be49003e2801597b07a5fab9fd50bcaa7c2708"
target_path: "D:\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\commission\\views\\CommissionAdjustmentsView.vue"
timestamp: 2026-10-28T10-30-31Z
slug: ssion-views-commissionadjustmentsview-vue-5eec06c0
---
Method: dual-agent (A: /root/adjustments_list_design · B: /root/adjustments_list_evidence)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|---|---:|---|
| 1 | Visibility of system status | 2/4 | Loading and error copy exists, but active filters have no visible summary or reset. |
| 2 | Match system / real world | 2/4 | “Adjustment” and “Paid / Finalized” leak internal terminology. |
| 3 | User control and freedom | 2/4 | Filters are reversible one by one, but no clear-all or reset from an empty result. |
| 4 | Consistency and standards | 3/4 | Shared controls are coherent, but Thai UI mixes English lifecycle terms. |
| 5 | Error prevention | 2/4 | High-impact creation starts from a generic CTA with no eligibility cue. |
| 6 | Recognition rather than recall | 2/4 | The table exposes IDs but not a useful reason, impact, or next step. |
| 7 | Flexibility and efficiency | 1/4 | No sorting, saved views, bulk review, or keyboard fast path. |
| 8 | Aesthetic and minimalist design | 3/4 | Clean composition, but status and money risk receive too little hierarchy. |
| 9 | Error recovery | 2/4 | Error alert has no retry; empty results have no clear-filter action. |
| 10 | Help and documentation | 1/4 | No contextual explanation of adjustment lifecycle or statuses. |
| **Total** | | **20/40** | **Acceptable — significant decision-support improvements needed.** |

## Design Specificity Verdict

The surface is orderly and consistent with the clinic palette, but it is still category-interchangeable: title, tabs, filters, and a generic data table do not explain why a compensation adjustment exists or which record needs attention. The deterministic scan found no rule violations in `CommissionAdjustmentsView.vue`; manual evidence confirms real loading, error, empty, responsive, and accessible-label states. Browser overlay was unavailable because Windows browser automation could not establish the Chrome URL safely, so no injection occurred.

## Overall Impression

A solid operational skeleton, but it reads as a record directory rather than a reconciliation workspace. The biggest opportunity is to let a manager understand priority, cause, and financial consequence without opening every detail page.

## What's Working

- Clear structure: title, creation route, tabs, filters, count, table, and empty row are present.
- Search, month, and status controls are recognizable and limited to three decisions.
- Source implements responsive filter grids and a sticky row action at compact sizes, plus visible loading and alerting error states.

## Priority Issues

1. **[P1] The list does not support triage.**
   - **Why it matters:** Pending and completed rows look nearly identical. A manager cannot scan for the adjustment that needs action or understand its monetary consequence.
   - **Fix:** Use semantic status badges and a short reason/source descriptor; prioritize pending items or provide a pending summary. Pair positive/negative amounts with plain-language labels, not color alone.
   - **Suggested command:** `$impeccable clarify`

2. **[P1] Lifecycle language mixes internal English terms with Thai operational copy.**
   - **Why it matters:** “Adjustment” and “Paid / Finalized” force first-time managers to translate product vocabulary before acting.
   - **Fix:** Localize these labels or define them once where the state appears; differentiate “สถานะรอบ” from “สถานะรายการปรับปรุง” in plain Thai.
   - **Suggested command:** `$impeccable clarify`

3. **[P2] Filter state is not inspectable or recoverable.**
   - **Why it matters:** After filtering, the count does not say which conditions are active. An empty state gives no shortcut back to the full list.
   - **Fix:** Show active-filter chips and one reset action; add a clear-filter action to the empty result.
   - **Suggested command:** `$impeccable polish`

4. **[P2] The mobile table keeps only the action visible while identity can scroll away.**
   - **Why it matters:** On a narrow screen users can see “ดูรายละเอียด” but lose the patient/staff context that explains the row.
   - **Fix:** Switch rows to compact cards at narrow widths, or pin employee identity alongside the action with a visible horizontal-scroll affordance.
   - **Suggested command:** `$impeccable adapt`

5. **[P2] Recovery and guidance are incomplete.**
   - **Why it matters:** A generic API error may appear without a retry, and new managers receive no explanation of the adjustment lifecycle.
   - **Fix:** Add a retry control next to the error. Use a contextual info affordance for concise lifecycle help; do not add a permanent explanatory block.
   - **Suggested command:** `$impeccable harden`

## Persona Red Flags

- **Alex (power user):** Must inspect adjustments one at a time; there is no sort, bulk review, saved filter, or keyboard shortcut. Search covers only employee and reference.
- **Jordan (first-timer):** Cannot distinguish the two status columns or decode `SALE-`, `COURSE-`, `VISIT-`, and `FEE-` references without prior system knowledge.
- **Sam (accessibility-dependent user):** Negative value is mainly red; loading lacks a semantic busy state; the 920px table creates a long keyboard path on narrow screens despite good visually-hidden labels and an alerting error message.

## Minor Observations

- Every state other than `Applied` is rendered as “รอดำเนินการ”, which can hide future workflow states.
- A zero amount would render `-฿0.00` because the formatter treats zero as negative.
- The table footer repeats the count and provides a caveat too late to guide the initial decision.

## Questions to Consider

- What should a manager understand within five seconds that makes one adjustment worth opening before another?
- If pending adjustments matter most, should the default list lead with them rather than simply showing all records?
- Can each reference show a human-readable cause without exposing extra accounting complexity?
