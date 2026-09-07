# Sabyeguyz Clinic ERP

Monorepo: Vue frontend, Spring Boot backend, PostgreSQL database, and deployment configuration.

## Run locally with Docker

Install Docker Desktop with Linux containers and Docker Compose, then run from this repository root:

```bash
docker compose -f deploy/docker-compose.yml up --build -d
docker compose -f deploy/docker-compose.yml ps
```

Wait for the backend health endpoint to return `{"status":"UP"}` before using the application:

- Frontend: http://localhost:5173/patients
- Register patient: http://localhost:5173/patients/register
- Backend health: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui/index.html

The frontend serves a production build through nginx. Browser requests to `/api/` are forwarded to the backend on the Compose network. No local Node.js, Java, or Maven installation is required.

```bash
docker compose -f deploy/docker-compose.yml logs --tail=100 backend frontend
docker compose -f deploy/docker-compose.yml down
```

Database data remains in the Compose volume after `down`. See [deployment instructions](deploy/README.md) for port overrides, test steps, and database details.

## Development workflow

Create `feat/<feature-name>` branches from `develop`, then open a pull request into `develop` after verification.
