# Local full-stack Docker Compose

Run from the repository root with Docker Desktop (Linux containers) running:

Copy `.env.example` to `.env`, then set unique `POSTGRES_PASSWORD` and `JWT_SECRET` values.

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

| Service | Local address | Purpose |
| --- | --- | --- |
| Frontend | http://localhost/patients | Vue production build served by nginx |
| Backend | http://localhost:8080 | Spring Boot REST API |
| PostgreSQL | localhost:5432 | Database `clinic_erp`, local user `clinic` |

Do not use real patient data or production credentials in this setup. Set the database and JWT secrets in `.env` before starting Compose.

PostgreSQL must become healthy before the backend starts. Spring Boot applies Flyway migrations and validates the schema. The frontend health check at `/healthz` checks nginx only; wait for http://localhost:8080/actuator/health to return `{"status":"UP"}` before using patient operations.

nginx forwards `/api/` requests to `backend:8080` without removing the `/api/` prefix. Vue routes such as `/patients/1` fall back to `index.html`, so direct navigation and refresh work. `VITE_API_BASE_URL` is set to an empty string when building the image to use this same-origin proxy. Vite settings are embedded at build time; container runtime environment changes do not rewrite the frontend bundle.

## Verify patient flow

Use synthetic data only:

1. Open http://localhost/patients/register and complete registration with a unique username, email, and identity number. The existing form selects branch `RP`.
2. Record the HN shown after successful registration.
3. Open http://localhost/patients and search for that HN.
4. Open the patient profile and verify the submitted details.
5. Refresh the profile URL to verify Vue route fallback.
6. Stop and start the same Compose project, then search for the HN again to verify persistence.

```bash
docker compose logs --tail=100 backend frontend
docker compose down
docker compose up -d
```

`down` keeps the database volume. Do not add `--volumes` unless you intend to delete that project's local database.

## Port conflicts and isolated verification

Override host ports in PowerShell, then use a separate project name for an isolated database volume:

```powershell
$env:FRONTEND_PORT = '15173'
$env:BACKEND_PORT = '18080'
$env:POSTGRES_PORT = '15432'
docker compose -p clinic-fullstack-check up --build -d
```

Use http://localhost:15173/patients and http://localhost:18080/actuator/health with these settings. Use the same `-p clinic-fullstack-check` option for `ps`, `logs`, and `down`. Internal service ports and the nginx proxy remain unchanged.

Remove these process-level overrides before returning to the default ports:

```powershell
Remove-Item Env:FRONTEND_PORT, Env:BACKEND_PORT, Env:POSTGRES_PORT -ErrorAction SilentlyContinue
```

## Google Calendar sync (optional)

To turn on the two-way Google Calendar sync, add `compose.calendar.yaml` and a `CALENDAR_KEY_FILE` entry to `.env`; see `docs/deploy-calendar-sync.md`.
