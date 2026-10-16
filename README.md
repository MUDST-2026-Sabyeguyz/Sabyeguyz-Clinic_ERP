# Sabyeguyz Clinic ERP

Monorepo: Vue frontend, Spring Boot backend, PostgreSQL database, and deployment configuration.

## Run locally with Docker

Install Docker Desktop with Linux containers and Docker Compose v2. From a clean clone of the `main` branch, run these commands from the repository root:

```bash
git clone --branch main --single-branch https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
docker compose -f deploy/docker-compose.yml config --quiet
docker compose -f deploy/docker-compose.yml up --build -d
docker compose -f deploy/docker-compose.yml ps
```

Wait for the backend health endpoint to return `{"status":"UP"}` before using the application:

- Frontend: http://localhost:5173/patients
- Register patient: http://localhost:5173/patients/register
- Backend health: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui/index.html

The frontend serves a production build through nginx. Browser requests to `/api/` are forwarded to the backend on the Compose network. No local Node.js, Java, or Maven installation is required.

For the step-by-step synthetic patient registration, profile readback, page refresh, and persistence check, follow [deployment instructions](deploy/README.md). That run document also covers port overrides, logs, and database details.

```bash
docker compose -f deploy/docker-compose.yml logs --tail=100 backend frontend
docker compose -f deploy/docker-compose.yml down
```

Database data remains in the Compose volume after `down`. See [deployment instructions](deploy/README.md) for port overrides, test steps, and database details.

## Development workflow

Create `feat/<feature-name>` branches from `develop`, then open a pull request into `develop` after verification.
