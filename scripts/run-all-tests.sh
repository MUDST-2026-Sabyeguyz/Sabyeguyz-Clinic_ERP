#!/usr/bin/env bash
# Runs the project's whole test suite on this machine (the same checks the GitHub workflows run), so a change
# can be verified without GitHub Actions minutes.
#
#   bash scripts/run-all-tests.sh            # everything (needs Docker)
#   bash scripts/run-all-tests.sh quick      # frontend ESLint, type check, unit tests, build (no Docker)
#   bash scripts/run-all-tests.sh backend    # backend HTTP tests (Cypress against the v2 app on PostgreSQL)
#   bash scripts/run-all-tests.sh frontend   # quick checks + frontend browser tests (Cypress)
#
# What it starts (and removes again at the end): a throw-away PostgreSQL 16 container, the v2 backend in a Maven
# container on 127.0.0.1:18080, and the Vite dev server on localhost:5173. Those two ports must be free.
# Needs: Docker, Node.js (see frontend/.nvmrc), Git Bash/Bash. The first run downloads Maven packages (a few minutes);
# they are kept in the Docker volume "sabyeguyz-m2" (override with M2_VOLUME=...).
#
# If the backend stops with Flyway's "more than one migration with version 1", delete backend/target and run again.
set -u

mode="${1:-all}"
case "$mode" in all | quick | backend | frontend) ;; *) echo "usage: bash scripts/run-all-tests.sh [all|quick|backend|frontend]"; exit 2 ;; esac

root="$(cd "$(dirname "$0")/.." && pwd)"
root_native="$(cd "$root" && (pwd -W 2>/dev/null || pwd))" # a path Docker understands, also on Windows
m2_volume="${M2_VOLUME:-sabyeguyz-m2}"
net="sabyeguyz-test-net"; db="sabyeguyz-test-pg"; app="sabyeguyz-test-app"
logs="$(mktemp -d)"
results=()
failed=0
vite_pid=""

say() { printf '\n==> %s\n' "$*"; }
record() { # name, exit code
  if [ "$2" = "0" ]; then results+=("PASS  $1"); else results+=("FAIL  $1"); failed=1; fi
}
stage() { # name, command...   (output goes to the log, only the verdict is shown)
  local name="$1"; shift
  say "$name"
  if "$@" >"$logs/${name// /_}.log" 2>&1; then record "$name" 0; echo "    ok"; else
    record "$name" 1; echo "    FAILED - last lines of the log:"; tail -n 25 "$logs/${name// /_}.log" | sed 's/^/    | /'
  fi
}
port_busy() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null ||
    curl -s -o /dev/null -m 2 "http://127.0.0.1:$1/" ||
    curl -s -o /dev/null -m 2 "http://localhost:$1/"
}

cleanup() {
  [ -n "$vite_pid" ] && kill "$vite_pid" 2>/dev/null
  if command -v powershell >/dev/null 2>&1 && [ -n "$vite_pid" ]; then
    powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id \$_.OwningProcess -Force }" >/dev/null 2>&1
  fi
  if command -v docker >/dev/null 2>&1; then
    docker rm -f "$app" "$db" >/dev/null 2>&1
    docker network rm "$net" >/dev/null 2>&1
  fi
}
trap cleanup EXIT

npm_ci_if_needed() { [ -d "$1/node_modules" ] || (cd "$1" && npm ci); }

# ---------- frontend: static checks and unit tests (no Docker) ----------
if [ "$mode" != "backend" ]; then
  say "Installing frontend packages if missing"
  npm_ci_if_needed "$root/frontend" >"$logs/npm-frontend.log" 2>&1 || { echo "npm ci failed:"; tail -n 20 "$logs/npm-frontend.log"; exit 1; }
  stage "frontend eslint" bash -c "cd '$root/frontend' && npx eslint ."
  stage "frontend unit tests" bash -c "cd '$root/frontend' && npx vitest run"
  stage "frontend type check and build" bash -c "cd '$root/frontend' && npm run build"
fi

# ---------- anything below needs Docker ----------
if [ "$mode" != "quick" ]; then
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || { echo "Docker is not running. Start Docker Desktop, or use: bash scripts/run-all-tests.sh quick"; exit 1; }
  for port in 18080 5173; do
    if [ "$port" = 5173 ] && [ "$mode" = "backend" ]; then continue; fi
    port_busy "$port" && { echo "Port $port is already in use; stop that server first (this script refuses to touch it)."; exit 1; }
  done

  say "Starting PostgreSQL and the v2 backend (first run downloads packages; can take several minutes)"
  docker network create "$net" >/dev/null 2>&1
  docker run -d --name "$db" --network "$net" -e POSTGRES_USER=clinic -e POSTGRES_PASSWORD=clinic -e POSTGRES_DB=clinic \
    postgres:16-alpine >/dev/null || { echo "could not start PostgreSQL"; exit 1; }
  for _ in $(seq 1 30); do docker exec "$db" pg_isready -U clinic -d clinic >/dev/null 2>&1 && break; sleep 2; done
  MSYS_NO_PATHCONV=1 docker run -d --name "$app" --network "$net" -p 127.0.0.1:18080:18080 \
    -e DATABASE_URL="jdbc:postgresql://$db:5432/clinic" -e DATABASE_USERNAME=clinic -e DATABASE_PASSWORD=clinic \
    -e SERVER_ADDRESS=0.0.0.0 \
    -v "$root_native/backend:/app" -v "$m2_volume:/root/.m2" -w /app maven:3.9-eclipse-temurin-21 \
    mvn --batch-mode --no-transfer-progress -Pcypress-v2 test-compile spring-boot:run >/dev/null || { echo "could not start the backend"; exit 1; }

  up=0
  for _ in $(seq 1 120); do # up to 10 minutes
    if curl -s -o /dev/null -m 3 http://127.0.0.1:18080/api/auth/login; then up=1; break; fi
    docker ps --format '{{.Names}}' | grep -qx "$app" || break
    sleep 5
  done
  if [ "$up" != "1" ]; then
    echo "The backend did not start. Last lines of its log:"; docker logs --tail 40 "$app" 2>&1 | sed 's/^/    | /'
    record "start the v2 backend" 1
  else
    record "start the v2 backend" 0

    if [ "$mode" != "frontend" ]; then
      npm_ci_if_needed "$root/backend" >"$logs/npm-backend.log" 2>&1
      stage "backend HTTP tests (Cypress)" bash -c "cd '$root/backend' && npx cypress run --config-file cypress.v2.config.cjs"
    fi

    if [ "$mode" != "backend" ]; then
      say "Starting the frontend dev server"
      (cd "$root/frontend" && VITE_API_PROXY_TARGET=http://127.0.0.1:18080 npx vite --host localhost --port 5173 --strictPort >"$logs/vite.log" 2>&1) &
      vite_pid=$!
      for _ in $(seq 1 30); do curl -s -o /dev/null -m 2 http://localhost:5173/ && break; sleep 2; done
      if ! kill -0 "$vite_pid" 2>/dev/null || ! curl -s -o /dev/null -m 2 http://localhost:5173/; then
        echo "The frontend dev server did not start. Last lines of its log:"
        tail -n 20 "$logs/vite.log"
        record "start the frontend dev server" 1
      else
        if [ -n "${CYPRESS_SPEC:-}" ]; then
          stage "frontend browser tests (Cypress)" bash -c 'cd "$1/frontend" && CYPRESS_API_URL=http://127.0.0.1:18080 npx cypress run --config-file cypress.v2.config.cjs --browser chrome --spec "$2"' _ "$root" "$CYPRESS_SPEC"
        else
          stage "frontend browser tests (Cypress)" bash -c "cd '$root/frontend' && CYPRESS_API_URL=http://127.0.0.1:18080 npx cypress run --config-file cypress.v2.config.cjs --browser chrome"
        fi
      fi
    fi
  fi
fi

# ---------- summary ----------
printf '\n================ SUMMARY ================\n'
for line in "${results[@]}"; do echo "$line"; done
if [ "$failed" = "0" ]; then echo "ALL TESTS PASSED"; else echo "SOME TESTS FAILED (logs in $logs)"; fi
exit "$failed"
