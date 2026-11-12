# Clinic ERP Kubernetes deployment template

`clinic-erp.template.yaml` contains backend/frontend Deployments and Services,
a PostgreSQL 16 StatefulSet with a 5 Gi PVC, ConfigMap, and Ingress. Render the
image refs, Ingress class/host and StorageClass for the chosen target before
applying with an explicit context and namespace. The template has no Secret values.

## Local target and current limits

Local feasibility on 2026-10-04 used Minikube v1.39.0, profile/context
`clinic-phase1`, Kubernetes v1.36.1, namespace `clinic-erp-phase1`, Traefik class
`traefik`, host `localhost`, and default StorageClass `standard`
(`k8s.io/minikube-hostpath`). UI, authenticated patient write/read, Flyway V1,
and persistence after application/PostgreSQL pod recreation passed locally.
This used Compose-built local images; it does not prove a GitHub build,
private registry access, self-hosted runner, GitHub deployment or release readiness.

The namespace-neutral template routes `/api` to backend and `/` to frontend.
The local feasibility run also used an additional Traefik StripPrefix
middleware for `/clinic-erp/api` to `/api`. That rendered local configuration
is not included in this generic template; supply a separate prefixed Ingress
and middleware if the target requires that alias. Do not change the frontend
base/router to simulate Ingress proof.

## Provision and validate

1. Verify the selected context, namespace, Ingress controller and StorageClass.
   The template omits `storageClassName`; record and pin the actual provisioner
   before first apply. Preserve existing PVCs and database data.
2. Replace backend/frontend image placeholders with tested immutable image
   digests. Local Minikube may load local images for feasibility; a GitHub
   deployment must prove registry pull access and runner reachability separately.
3. Provision `clinic-erp-runtime` out of band with `POSTGRES_USER`,
   `POSTGRES_PASSWORD`, and `JWT_SECRET`. Keep values out of Git, command history,
   YAML and logs. Bootstrap defaults to disabled; enable once with a securely
   provisioned admin credential, then disable and remove that bootstrap credential.
4. Validate the rendered YAML using server dry-run before applying. Wait for
   PostgreSQL, backend Flyway completion and frontend readiness; stop on setup,
   migration, seed, rollout or smoke failure.
5. Verify UI assets, Manager login, synthetic patient create/read/search through
   Ingress, then recreate application/PostgreSQL pods and confirm the same
   patient and successful Flyway history remain. Record image digests, context,
   PVC identity and status codes without credentials or authentication tokens.

Backend startup/liveness use TCP 8080 because the current security config
returns 401 on health subpaths. Readiness uses public `/actuator/health`, which
includes datasource and database-v2 health. A TCP liveness probe checks the
listening socket only; it does not prove API or DB correctness. Frontend probes
use `/healthz`; PostgreSQL uses `pg_isready`. Reverify probes for each image.

Backend uses `Recreate` to keep a single startup migration runner, causing a
brief API outage during upgrades. Resource limits are initial values to tune
from measurements. Current images require verification before adding a
`runAsNonRoot` policy.

## Database changes, backup and rollback

The active profile is `database-v2`, with Flyway at `db/migration_new`.
The candidate `feat/database` schema at `1e606c20` remains on HOLD: renamed
columns and receipt/sale/course/payment relationships do not match current
application callers. Do not merge it or overwrite applied V1 history.

Use forward migrations/backfill after reviewing contracts and requirements.
Test fresh schema and upgrade from old V1 with representative data separately.
Before moving real data, take a protected PostgreSQL custom-format backup
(`pg_dump -Fc`) outside the cluster and verify restore in a separate disposable
database/namespace. Restore rehearsal is still outstanding.

Roll images back by digest only when compatible with the current schema.
Image rollback does not undo a migration. Never delete the active PVC as cleanup
or treat pod recreation as a backup/restore test. Q01-Q03 regression tests and
function/assertion coverage gaps remain open; this checkpoint is not a release gate.
