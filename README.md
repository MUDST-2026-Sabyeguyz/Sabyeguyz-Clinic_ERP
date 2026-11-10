# Sabyeguyz Clinic ERP

Monorepo: Vue frontend, Spring Boot backend, PostgreSQL database, and deployment configuration.

## Quick Start

Requirements: Git and Docker with Docker Compose. No local Java, Maven, Node.js, or PostgreSQL installation is required.

```bash
git clone https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
cp .env.example .env
# Set unique POSTGRES_PASSWORD and JWT_SECRET values in .env.
docker compose up --build -d
```

Services:

- Frontend: http://localhost
- Backend health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- PostgreSQL: localhost:5432

The first startup runs Flyway migrations automatically. PostgreSQL data is stored in the project's `postgres-v2-data` Docker volume and survives `docker compose down` followed by `docker compose up -d`. Use `docker compose down -v` only when you intentionally want to remove the database volume.

Demo seed data is disabled by default, including when `CLINIC_SEED_MODE` is omitted. To load the full synthetic dataset, use a disposable local database and explicitly set `CLINIC_DATABASE_KIND=local-demo` and `CLINIC_SEED_MODE=full` in `.env` before startup. `minimal` selects the smaller legacy demo; `none` runs schema migrations and reference data only. Demo modes are rejected for `prod`/`production` Spring profiles and when the database kind is not explicitly `local-demo`. A database with an existing seed history must continue using that same mode; use a separate empty database to change modes. Flyway never removes demo rows already present in an existing database.

The Compose project is named `sabyeguyz`. Data from an older Compose project stays in its previous volume and is not moved into the new project automatically.

Copy `.env.example` to `.env` and set unique database and JWT secrets before starting Compose. Ports can also be changed there.

## Update an existing Windows installation

Run from the existing repository folder. This rebuilds and replaces the frontend and backend using the running application's Compose project and its existing database volume. Keep the installation's current `.env`.

```powershell
git switch main
git pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File scripts/update-local.ps1
```

If the application is stopped and was originally started with a custom project name, pass that same name: `powershell -ExecutionPolicy Bypass -File scripts/update-local.ps1 -ProjectName clinic-hotfix-merging`.

After the update, refresh the browser with Ctrl+F5 once. Payment settings are at `/staff/settings`; appointments are at `/staff/appointments`. A build replaces application code; each installation keeps its own saved payment settings and patient data.

## Local Docker verification

Install Docker Desktop with Linux containers and Docker Compose v2. From a clean clone of the `main` branch, run these commands from the repository root:

```bash
git clone --branch main --single-branch https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
cp .env.example .env
# Set unique POSTGRES_PASSWORD and JWT_SECRET values in .env.
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

Wait for the backend health endpoint to return `{"status":"UP"}` before using the application:

- Frontend: http://localhost/patients
- Register patient: http://localhost/patients/register
- Backend health: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui/index.html

The frontend serves a production build through nginx. Browser requests to `/api/` are forwarded to the backend on the Compose network. No local Node.js, Java, or Maven installation is required.

For the step-by-step synthetic patient registration, profile readback, page refresh, and persistence check, follow [deployment instructions](deploy/README.md). That run document also covers port overrides, logs, and database details.

```bash
docker compose logs --tail=100 backend frontend
docker compose down
```

Database data remains in the Compose volume after `down`. See [deployment instructions](deploy/README.md) for port overrides, test steps, and database details.

## Jenkins deployment to local Kubernetes

For the Windows presentation machine with Docker Desktop Kubernetes and Jenkins on port 8085, run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-jenkins-kubernetes.ps1`. It connects a Windows agent, creates the `Clinic-Kubernetes` Pipeline and queues the build/deployment. See [Kubernetes presentation instructions](deploy/kubernetes/README.md).

## Development workflow

Create `feat/<feature-name>` branches from `develop`, then open a pull request into `develop` after verification.

### Frontend tests

Use Node.js 24 as specified by `frontend/.nvmrc`. From `frontend/`, run `npm ci`, `npm run test:unit -- --run`, and `npm run build`. For E2E, first start PostgreSQL and backend from the repository root with `docker compose up --build -d postgres backend`; then run `npm run dev -- --host localhost` in one terminal and `npm run test:e2e -- --browser chrome` in another. Cypress tests exercise the login form against the real backend. The GitHub Actions workflow starts the backend and Vite server before running the same Cypress command in Chrome.
