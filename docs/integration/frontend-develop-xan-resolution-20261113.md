# Frontend resolution: develop and Xan Phase 2

Compared the rewritten remote tips `b622a7aa` (develop) and `0b85ea1e` (Xan) before resolving frontend conflicts. This is a content decision, not a claim that every historical interface remains active.

| Domain | Resolution and reason |
|---|---|
| Authentication and routing | Retained the Phase 2 manager branch selection and staff routes. The older develop login skipped branch selection; restoring it would break the selected-branch contract used by current workspaces. |
| Patients and appointments | Retained separate manager/staff workspaces, card-reader integration, backend-resolved optional duration, appointment progress actions, calendar workspace and current regression tests. Develop predates these components and its smaller pages do not replace their contracts. |
| Courses and transactions | Retained validated direct course URLs, actual patient selection, payment collections, frozen usage print documents and current cancellation reason handling. The older alternate receipt/refund/course screens are preserved as historical source rather than connected to modern routes with incompatible assumptions. |
| Reports | Retained detailed sales/payment reports, filters, all-page CSV and role controls instead of restoring the earlier aggregate-only report workspace. |
| Compensation settings | Retained the modern branch-aware create-rule drawer and added develop's manual missing-single-visit-fee backfill form alongside it. The existing API receives only `from`/`to`; the existing backend Manager permission and backfill scope are unchanged. The form does not pretend to filter the operation to the selected branch. |
| Version/audit views | The develop and Xan version-history views already implement the same core behavior; differences were mostly styles/type casts. Retained current components and the additional `nextSchemeEffectiveFrom` helper. |
| Personnel detail | Checked develop's explicit removal of the unsupported disabled send-back button and hardcoded Adjustment-zero note. Both are already absent from the retained Phase 2 markup. |
| Build and package configuration | Retained the consistent Xan Node/package lock/build configuration, rather than combining duplicate Cypress configuration entry points from the older tree. |

Develop-only files are preserved verbatim under `develop-frontend-source-20261113/` with a source-SHA manifest. They include older alternative routes and tests that assert the earlier DOM. They are not counted as executed tests or active features. Both merge parents retain the original source history.

Added two mounted regression scenarios for coexistence of the modern drawer and the restored backfill form, pending duplicate submission protection, date validation and API failure display. Execution evidence is recorded by the integrating agent; authorship of this resolution alone is not a passing-test claim.
