# Manager E2E results — 17 October 2026

## Appointment confirmation and checkout

Final run: **1 test / 1 passed / 0 failed**, Cypress 16.1.0, Chrome 155 headless.

The browser flow created an unconfirmed appointment in the disposable `patients_bcc` database, changed its status from the Manager appointment list, and verified navigation to `/staff/services` with the same patient's `patientId` and the appointment's `appointmentId`. The appointment remained `UNCONFIRMED` before checkout. After a Cash sale was saved, the confirm API changed it to `CONFIRMED`; the test verified the appointment via API and its confirmed label after returning to `/staff/appointments`.

The isolated PostgreSQL container and the backend and Vite processes used for this run were stopped after verification. The local Clinic ERP Docker stack was not used as the E2E database.

## Run this workflow

Use the isolated `cypress-v2` backend on `127.0.0.1:18080`, Vite on `localhost:5173` proxying to that backend, and set `MANAGER_E2E_RESET=1` only for the disposable database. From `frontend`:

```powershell
$env:MANAGER_E2E_RESET='1'
$env:VITE_API_PROXY_TARGET='http://127.0.0.1:18080'
npx cypress run --config-file cypress.manager.config.ts --browser chrome
```

The manager support hook resets fixtures before every test and verifies that the Vite proxy reaches the isolated backend.
