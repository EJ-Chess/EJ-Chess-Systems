# Docker — EJa Chess

Alle drei Dienste laufen als eigenständige Container und werden über `docker-compose` orchestriert.

---

## Schnellstart

### Schritt 1: Quarkus-JARs bauen

```bash
# Verzeichnis: Projektroot
./gradlew :modules:chess-api:quarkusBuild :modules:bot-service:quarkusBuild
```

Dauert ~1–2 Minuten. Output: `modules/chess-api/build/quarkus-app/` und `modules/bot-service/build/quarkus-app/`

### Schritt 2: Docker Compose starten

```bash
# Verzeichnis: Projektroot
docker-compose up --build -d
```

Alle drei Container starten detached (`-d`). Nach ~30 Sekunden sind Health-Checks grün.

| Dienst | URL |
|--------|-----|
| Web-UI | http://localhost:5173 |
| Game-Service | http://localhost:8080 |
| Bot-Service | http://localhost:8081 |
| Swagger (game) | http://localhost:8080/q/swagger-ui |
| Swagger (bot) | http://localhost:8081/q/swagger-ui |
| Health (game) | http://localhost:8080/q/health |
| Health (bot) | http://localhost:8081/q/health |

---

## Dienste stoppen

```bash
# Verzeichnis: Projektroot
docker-compose down
```

---

## Architektur im Docker-Netz

```
Browser
   │  HTTP :5173
   ▼
chess-ui  (nginx:alpine, Port 80)
   │  proxy_pass /games → http://game-service:8080
   │
   ▼
game-service  (eclipse-temurin:21-jre, Port 8080)
   │  HTTP POST /bot/move   (nur bei Bot-Partien)
   │  env: BOT_SERVICE_URL=http://bot-service:8081
   ▼
bot-service  (eclipse-temurin:21-jre, Port 8081)
```

Im Docker-Netz kommunizieren die Container **nicht** über `localhost`,
sondern über die Service-Namen (`game-service`, `bot-service`).
Das Environment-Variable `BOT_SERVICE_URL` überschreibt den MicroProfile-Config-Wert
aus `application.properties` automatisch (Konvention: `bot-service.url` → `BOT_SERVICE_URL`).

---

## Dockerfile-Übersicht

| Dienst | Datei | Strategie |
|--------|-------|-----------|
| `game-service` | `modules/chess-api/Dockerfile` | Pre-built JVM (Quarkus fast-jar) |
| `bot-service` | `modules/bot-service/Dockerfile` | Pre-built JVM (Quarkus fast-jar) |
| `chess-ui` | `modules/chess-ui/Dockerfile` | Multi-stage: Node build → nginx serve |

### Warum pre-built für die Quarkus-Dienste?

Quarkus benötigt alle Gradle-Submodule für den Build (wegen `settings.gradle.kts`).
Ein Multi-stage-Build mit Gradle inside Docker würde das gesamte Projekt laden
(inkl. JavaFX-Downloads) und wäre für das Dev-/Demo-Szenario deutlich langsamer.
Der Makefile-Workflow (erst Gradle, dann Docker) ist schneller und übersichtlicher.

---

## Health Checks

docker-compose wartet mit dem Start des nächsten Dienstes bis der vorherige
seinen `/q/health/ready`-Endpunkt beantwortet:

```
bot-service  →  (service_healthy)  →  game-service  →  (service_healthy)  →  chess-ui
```

Das verhindert, dass der Game-Service startet, bevor der Bot-Service erreichbar ist.

---

---

## Weiterführend

- Resilience (Health, Circuit Breaker): [docs/readme/resilience.md](resilience.md)
- Microservice-Architektur: [docs/readme/microservice-approach-a.md](microservice-approach-a.md)
