.PHONY: dev-start dev-rebuild dev-stop dev-logs backend-build backend-run backend-test frontend-install frontend-build frontend-run frontend-test docker-build-backend docker-run-backend docker-build-frontend docker-run-frontend test verify

dev-start:
	docker compose --env-file .env up -d
dev-rebuild:
	docker compose --env-file .env up --build -d
dev-stop:
	docker compose --env-file .env down
dev-logs:
	docker compose --env-file .env logs --tail=100 -f

backend-build:
	cd backend && mvn -B package -DskipTests
backend-run:
	cd backend && mvn spring-boot:run
backend-test:
	cd backend && mvn -B test

frontend-install:
	cd frontend && npm ci
frontend-build:
	cd frontend && npm run build
frontend-run:
	cd frontend && npm run dev
frontend-test:
	cd frontend && npm run test:unit -- --run

docker-build-backend:
	docker build -t sabyeguyz-backend ./backend
docker-run-backend:
	docker run --rm --env-file .env -p $${BACKEND_PORT}:8080 sabyeguyz-backend
docker-build-frontend:
	docker build --build-arg VITE_API_BASE_URL=$${VITE_API_BASE_URL} -t sabyeguyz-frontend ./frontend
docker-run-frontend:
	docker run --rm -p $${FRONTEND_PORT}:80 sabyeguyz-frontend

test: backend-test frontend-test
verify: test backend-build frontend-build
