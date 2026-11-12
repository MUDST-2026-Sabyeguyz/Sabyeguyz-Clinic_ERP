# Code and verification standards

Use feature branches and a pull request into `develop`. Keep changes limited to the requested feature; preserve unrelated local work. Do not commit credentials, runtime folders, kubeconfig, patient data, dependency directories or build outputs.

## Readable code

- Keep Vue/TypeScript features under `frontend/src/features`, API calls in the existing API layer, and use typed request/response contracts. Run ESLint in check mode, TypeScript, component tests and build before review.
- Keep Spring controllers focused on HTTP contracts, business rules in services and persistence in repositories. Validate input and role/branch access on the backend. Java compiles with the Java 17 language level; container/CI builds use JDK 21.
- Preserve applied Flyway migrations; add forward migrations and verify on PostgreSQL. Do not use schema resets against development databases to make tests pass.
- Write behavior tests for validation, conflicts, permissions and readback, with clear synthetic fixtures. Distinguish mocked component tests, real HTTP integration tests and browser E2E in reports.
- Kubernetes configuration belongs in `deploy/k8s`; non-secret settings use ConfigMap, secrets are generated locally, database data uses PVC, and each workload has resource requests/limits and health probes.

## Required checks

From the repository root, `pwsh -File scripts/Test-Submission.ps1` checks frontend standards, tests, coverage/build, backend regressions and PostgreSQL integration plus browser E2E. It refuses occupied test ports and removes only resources it creates. See [the Kubernetes runbook](deploy/k8s/README_TH.md) for prerequisites and deployment acceptance.

For frontend-only iteration:

```sh
cd frontend
npm ci
npm run lint:check
npm run test:coverage
npm run build
```

`lint:check` uses the repository's ESLint configuration without `--fix`. Existing `lint:*` fix commands are for intentional developer edits. Oxlint has additional Vitest rules and needs its own compatibility cleanup; it is not the submission gate.

Coverage must include untested production files and report its actual scope and percentages. No minimum percentage has been supplied by the instructor; do not claim one or remove uncovered features to improve the score. A successful pipeline does not establish acceptance of every clinic business rule.

PR descriptions should identify the changed behavior, validation commands/results, source revision and any remaining limitations. For deployment evidence, connect source revision → image tag/digest → cluster workloads → authenticated runtime checks.
