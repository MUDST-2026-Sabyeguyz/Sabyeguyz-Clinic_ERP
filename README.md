# Sabyeguyz Clinic ERP

Monorepo: Vue frontend, Spring Boot backend, PostgreSQL database, and deployment configuration.

## Quick Start

Requirements: Git and Docker with Docker Compose. No local Java, Maven, Node.js, or PostgreSQL installation is required.

```bash
git clone https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
docker compose up --build -d
```

Services:

- Frontend: http://localhost
- Backend health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- PostgreSQL: localhost:5432

The first startup runs Flyway migrations automatically. PostgreSQL data is stored in the `postgres-data` Docker volume and survives `docker compose down` followed by `docker compose up -d`. Use `docker compose down -v` only when you intentionally want to remove the database volume.

Configuration defaults are documented in `.env.example`. Copy it to `.env` to override ports or database settings.

## Local Docker configuration

Install Docker Desktop with Linux containers and Docker Compose, then run from this repository root:

```bash
cp .env.example .env
# แก้ POSTGRES_PASSWORD และ JWT_SECRET ใน .env ให้เป็นค่าจริงของเครื่อง
docker compose up --build -d
docker compose ps
```

Wait for the backend health endpoint to return `{"status":"UP"}` before using the application:

- Frontend: http://localhost
- Login: http://localhost/
- Backend health: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui/index.html

The frontend serves a production build through nginx. Browser requests to `/api/` are forwarded to the backend on the Compose network. No local Node.js, Java, or Maven installation is required.

```bash
docker compose logs --tail=100 backend frontend
docker compose down
```

Database data remains in the Compose volume after `down`. See [deployment instructions](deploy/README.md) for port overrides, test steps, and database details.

## Development workflow

Create `feature/<feature-name>` branches from `develop`, then open a pull request into `develop` after verification.
