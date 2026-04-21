.PHONY: build docker-up docker-down docker-logs help

## Build Quarkus fast-jars (required before docker-compose up)
build:
	./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild

## Build Quarkus jars and start all three services in Docker (detached)
docker-up: build
	docker-compose up --build -d
	@echo ""
	@echo "  Services starting — wait ~30 s for health checks to pass"
	@echo ""
	@echo "  Web-UI          →  http://localhost:5173"
	@echo "  Game-Service    →  http://localhost:8080"
	@echo "  Bot-Service     →  http://localhost:8081"
	@echo "  Swagger (game)  →  http://localhost:8080/q/swagger-ui"
	@echo "  Swagger (bot)   →  http://localhost:8081/q/swagger-ui"
	@echo "  Health (game)   →  http://localhost:8080/q/health"
	@echo "  Health (bot)    →  http://localhost:8081/q/health"

## Stop and remove all containers
docker-down:
	docker-compose down

## Stream logs from all running containers
docker-logs:
	docker-compose logs -f

## Show this help
help:
	@grep -E '^##' Makefile | sed 's/## //'
