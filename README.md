# Sabyeguyz Clinic ERP

## Quick Start

Requirements: Git and Docker with Docker Compose. No local Java, Maven, Node.js, or PostgreSQL installation is required.

```bash
git clone https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
docker compose up --build
```

Services:

- Frontend: http://localhost
- Backend health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- PostgreSQL: localhost:5432

The first startup runs Flyway migrations automatically. PostgreSQL data is stored in the `postgres-data` Docker volume and survives `docker compose down` followed by `docker compose up -d`. Use `docker compose down -v` only when you intentionally want to remove the database volume.

Configuration defaults are documented in `.env.example`. Copy it to `.env` to override ports or database settings.
