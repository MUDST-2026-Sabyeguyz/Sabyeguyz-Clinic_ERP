# Prepare for final integration

This branch starts at `main` (`e172add379c54d8ba3a58df400d428bbf4dff85c`) and includes the `test-merge` snapshot (`ec0b692ad026d48f644c079ce8c28934c42286a5`). It is an integration candidate, not a release approval.

## Incoming work to review

- `feat/database` is already ancestry-contained in the imported `test-merge` snapshot. Its separate PR #66 remains open; reconcile it before final merge.
- `feat/dashboard-and-appointment-lock-backend` has commits beyond `main` and needs comparison against the imported frontend/backend behavior. Do not blindly replay duplicate patches.
- `feat/ops` is currently local only. Publish and integrate its reviewed commits after comparing with this branch, particularly Spring Boot/Flyway/Jackson, migrations, seed policy, Jenkins and Kubernetes deployment.
- `fix/sabye-safe-demo-and-deploy` is also local only. Compare its browser E2E, authorization and checkout fixes against this tree. Preserve the unique fixes; do not copy its older dependency baseline wholesale.
- Historical `feature/*`, `dev*`, `backup/*`, and other remote refs must be assessed for unique, still-required work. A branch name or ahead count alone does not justify merging it.

## Integration loop

1. Fetch current refs and capture source SHA, changed files, ancestry and patch equivalence.
2. Merge or selectively port one confirmed source at a time. Resolve conflicts using current requirements, then run relevant tests and record the outcome.
3. Push this integration branch to update the draft PR. Recheck mergeability against `main` and CI after each update.
4. When a source branch receives new commits, repeat step 1. GitHub does not automatically import those commits into this branch.
5. Keep this PR in draft until the branch is tested end to end and the owner resolves outstanding business rules.

Do not add secrets, live database dumps or generated test artifacts. This checklist records pending work; it does not assert the application is ready to merge.
