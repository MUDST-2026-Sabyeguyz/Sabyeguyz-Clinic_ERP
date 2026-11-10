# Jenkins to Docker Desktop Kubernetes (Windows presentation)

Run on the Windows machine where Docker Desktop Kubernetes is Ready. The setup starts a local Jenkins controller bound only to `127.0.0.1:8086`.

```powershell
git switch main
git pull --ff-only origin main
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-jenkins-kubernetes.ps1
```

The setup script downloads a Windows Java 21 runtime if needed, registers an exclusive `clinic-present` Windows agent, adds the `Clinic-Kubernetes` Pipeline, connects the agent over WebSocket and queues its initial build. Jenkins is bound to localhost at `http://127.0.0.1:8086`; it does not replace or modify another Jenkins controller.

The controller requires its existing Pipeline plugins (`workflow-aggregator`), as installed by `jenkins-backup/Dockerfile`. No Docker or Kubernetes plugin is needed for this pipeline. Use the same Windows login that runs Docker Desktop and has the working `docker-desktop` kubectl context.

## Build and deployment

Open `http://127.0.0.1:8086/job/Clinic-Kubernetes/`. The stages are:

1. Capture and clone the presentation machine's committed local `main` revision into the Jenkins workspace. This lets the private repository build without putting a GitHub token into the setup script.
2. Run frontend unit tests using Node 24 in Docker.
3. Build frontend and backend images tagged with source revision and Jenkins build number.
4. Import images into visible kind nodes, or use Docker Desktop's containerd image mirror when its system containers are hidden.
5. Run the isolated PostgreSQL migration Job, apply app manifests to `clinic-present`, retain an existing `clinic-secrets` Secret/PVC, and wait for PostgreSQL, backend and both frontend replicas to become Ready. Failure stops the job and prints deployment events/logs.

Backend Surefire reports must contain at least one executed test. Seed parameters default to `none` / `unspecified`; selecting `minimal` or `full` also requires `local-demo`, and the deploy script rejects non-local API server addresses.

This is a manual Jenkins build of the local `main` snapshot, not a GitHub webhook integration. For later updates run `git pull --ff-only origin main` in the presentation repository, then click **Build Now**. Rerun the setup script if the Jenkinsfile changed, or if the Windows agent stopped after reboot. Agent logs and its secret stay under `%LOCALAPPDATA%\ClinicJenkinsAgent`, outside the repository.

The backend image includes the schema migrations and optional SQL demo (`db/seed_v2_legacy`, V1100/V1101/V1102). Deployment defaults to `CLINIC_SEED_MODE=none`; demo data is loaded only when both `DatabaseKind=local-demo` and an explicit `SeedMode=full` (or `minimal`) are supplied on a local Docker Desktop or kind context. Production profiles reject demo seed modes. Existing databases with a seed history must keep the matching mode; use a new disposable database to change modes. Flyway does not remove previously loaded demo rows.

Do not deploy demo mode against a shared or production cluster. This local presentation flow does not copy a different machine's live database. Verify rollout and login on the presentation machine before presenting; local build success is not evidence of a remote rollout.

## Open the application and present

After the Jenkins build is green, keep this command running:

```powershell
kubectl --context docker-desktop -n clinic-present port-forward service/frontend 38001:80
```

Open `http://localhost:38001`. In another terminal:

```powershell
kubectl --context docker-desktop -n clinic-present get pods,services,pvc -o wide
kubectl --context docker-desktop -n clinic-present get pods -o wide -w
```

Show the green Jenkins stages, Ready pods, two frontend replicas and persistent PostgreSQL claim, then log into the application. Stop the port-forward with Ctrl+C after the demo. That does not delete workloads or data.

For manual deployment without Jenkins:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy-kubernetes.ps1
```

The command above defaults to schema/reference data only. For a disposable local demo database, explicitly add `-DatabaseKind local-demo -SeedMode full`.

If images are unavailable to kind nodes, turn on **Show system containers** in Docker Desktop Kubernetes and rerun the failed Jenkins build; the deployment script explicitly imports the source-tagged images into matching node containers. For `Pending` PVCs inspect `kubectl --context docker-desktop get storageclass` and the PVC events. For backend failures inspect `kubectl --context docker-desktop -n clinic-present logs deployment/backend --tail=100`.
