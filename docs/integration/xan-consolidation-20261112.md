# Xan branch consolidation — 12 November 2026

Target: `xan-merge/user-phase2-completion`, starting at rewritten remote `15625ac549938d6328352c50aed4677335e09f20`. Only current rewritten remote refs were merged. No pre-rewrite local history was used.

| Source remote branch | Captured source SHA | Preserved work |
|---|---|---|
| backup/present-environment | 0e99eae6f086c01a32fb3ec98116cfcaf300aa26 | Presentation docs, API/image-provenance/deployment tools, frontend base-path support |
| backup/draft-phase1 | 4c5170c27f576b0b3f7daca9b6b773ead486d509 | Coverage configuration, focused JUnit tests, local-start and review tools, historical Phase1 documentation |
| codex/jenkins-worker-init | 21ae4ab2742886ebfcaf407092d4ce6e2e20c216 | Jenkins worker/replay/watch utilities, isolated CI definitions, coverage/browser test tooling |
| feat/ops | 6be810ae005c23575e90064eaa3c0e637443adbe | Kubernetes deployment/migration/seed policy, Jenkins polling/no-op reporting, image scanning and local credentials tooling |

## Conflict decisions

- Current Phase2 course, commission and dashboard views retain their later fixes. Older deleted/reworked views and tests are retained as `.txt` source snapshots in `archived-branches/`, rather than reintroducing obsolete UI or silently changing current assertions.
- Superseded presentation CI definitions target obsolete schema/table names and deployment branches. Their full definitions are archived; the current CI configuration is retained. The repository-level Actions setting remains disabled.
- The application retains Spring Boot 3.4.5 and its Testcontainers/Cypress profiles. The Ops-only Spring Boot 4 dependency/compatibility variant is archived; imported migration/RuntimeCheck imports are adapted to the current framework. This consolidation does not claim to remediate all dependency vulnerabilities.
- Kubernetes migration/database separation and guarded seed modes from Ops are active. Existing non-root/resource/volume hardening is retained while database resources remain in `postgres.yaml`. No deployment was executed.
- Duplicate JaCoCo JVM-agent declarations were reconciled: Maven test coverage remains configured, and the optional coverage profile instruments the launched test application once.
- The original dirty checkout remains untouched. Its complete tracked patch and relevant untracked Java/SQL/deployment source are preserved in `archived-local-work/`. Its older automatic full-seed behavior is not enabled: the integrated policy requires an explicit local-demo database kind for non-none seeds. This is preservation of the alternative implementation, not a claim both conflicting policies can run simultaneously.
- Curated source/AC maps and requirement decisions are under `user-story-evidence/`. They are historical targeted evidence, not an updated claim of complete 483-AC coverage. Raw logs, runtime credentials, `.env`, `target`, `dist`, and `node_modules` are excluded.

## Verification on the consolidated files

- Backend `mvn -DskipTests package`: PASS, including test-source compilation. Tests were skipped by this packaging command.
- Focused Maven `DemoSeedPolicyTest,ClinicTimeTest,HnGeneratorServiceTest,DiscountTest`: 18 tests PASS, 0 failures/errors/skips.
- Frontend Vitest: 55 files / 312 tests PASS.
- Frontend production build/typecheck: PASS.
- All PowerShell files under scripts/: parser PASS; backend launcher Node syntax PASS; Compose config PASS.
- Jenkins replay Python suite: 3 tests, 1 failure on this Windows host. The failed fixture uses POSIX shell commands and failed before its intended assertion; it remains a limitation, not a passing check.
- Full Maven / actual browser V2 / Kubernetes runtime / CI deployment were not rerun here. The earlier Phase2 baseline full Maven was 158 tests with 2 failures and 1 error; do not infer that this consolidation closes those issues.

Logs remain outside Git in the parent directory as `xan-consolidation-*.log`. Original worktrees, databases, Jenkins configuration and demo deployments are unchanged. Remote source branches may be removed only after their captured tips are verified as ancestors of the pushed target. Local branches/worktrees are retained, especially the local `feat/ops` source Jenkins may still monitor.
