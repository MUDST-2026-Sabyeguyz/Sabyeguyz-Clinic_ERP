# Requirement decisions

## AC023.01 — HN running number

On 12 November 2026, the user explicitly chose **keep the existing 4-digit running number (0001–9999)** after being shown the conflict between the Phase 2 AC's 3 digits and the existing generator/data dictionary's 4 digits.

Acceptance for this work therefore uses `YYBBMMNNNN` for newly generated HNs. Existing patient HNs must remain unchanged. This is an explicit user requirement override; the source snapshot's Given/When/Then must remain verbatim, with this decision linked beside AC023.01. Do not claim the original 3-digit wording was implemented.
