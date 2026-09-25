.PHONY: dev start rebuild dev-start dev-rebuild backend-build backend-run frontend-build frontend-run test

dev: start

start: dev-start

rebuild: dev-rebuild

dev-start:
	docker compose up --build -d

dev-rebuild:
	docker compose build --no-cache

backend-build:
	cd backend && mvn --batch-mode clean package

backend-run:
	cd backend && mvn spring-boot:run

frontend-build:
	cd frontend && npm ci && npm run build

frontend-run:
	cd frontend && npm run dev

test:
	cd backend && mvn test
	cd frontend && npm run test:unit -- --run
