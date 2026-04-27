# Microservices — Was wurde gemacht (Abgabe-Übersicht)

Diese Datei beschreibt konkret welche Dateien neu erstellt oder geändert wurden,
und welche Stellen im Code die relevanten Konzepte zeigen.

---

## Was vorher da war (Monolith)

```
chess-api (Port 8080)
  └── Bot-Logik inline im selben Prozess
  └── JavaFX-GUI startete mit beim Server-Start
  └── chess-bot als compile-time Gradle-Dependency
```
```
chess-ui (Port 5173)  →  HTTP  →  game-service (Port 8080)  →  HTTP  →  bot-service (Port 8081)
```

---

| Datei | Was es ist |
|-------|-----------|
| [`modules/bot-service/src/main/scala/de/eljachess/botservice/BotResource.scala`](../../modules/bot-service/src/main/scala/de/eljachess/botservice/BotResource.scala) | REST-Endpoint `POST /bot/move` |
| [`modules/bot-service/src/main/scala/de/eljachess/botservice/BotEngine.scala`](../../modules/bot-service/src/main/scala/de/eljachess/botservice/BotEngine.scala) | Greedy-Random-Algorithmus (ELO-gesteuert) |
| [`modules/bot-service/src/main/scala/de/eljachess/botservice/dto/BotMoveRequest.scala`](../../modules/bot-service/src/main/scala/de/eljachess/botservice/dto/BotMoveRequest.scala) | Request-DTO |
| [`modules/bot-service/src/main/scala/de/eljachess/botservice/dto/BotMoveResponse.scala`](../../modules/bot-service/src/main/scala/de/eljachess/botservice/dto/BotMoveResponse.scala) | Response-DTO |
| [`modules/bot-service/src/main/resources/application.properties`](../../modules/bot-service/src/main/resources/application.properties) | Quarkus-Konfiguration (Port 8081) |
| [`modules/bot-service/build.gradle.kts`](../../modules/bot-service/build.gradle.kts) | Gradle-Modul (Quarkus, kein JavaFX, kein chess-bot) |

### Codestellen

**`BotResource.scala` — der REST-Endpoint (Zeilen 19–30)**
```scala
@POST
@Path("/move")
def getMove(req: BotMoveRequest): Response =
  val color = if req.color.toLowerCase == "black" then Color.Black else Color.White
  BotEngine.bestMove(req.fen, color, req.elo) match
    case Some((from, to)) => Response.ok(BotMoveResponse(from, to)).build()
    case None             => Response.status(422).entity(...).build()
```

---

## 2. Geänderter Service: game-service (chess-api)

### Neue Datei

| Datei | Was es ist |
|-------|-----------|
| [`modules/chess-api/src/main/scala/de/eljachess/chess/api/client/BotClient.scala`](../../modules/chess-api/src/main/scala/de/eljachess/chess/api/client/BotClient.scala) | HTTP-Client zum bot-service mit Fault Tolerance |
| [`modules/chess-api/src/main/scala/de/eljachess/chess/api/health/BotServiceHealthCheck.scala`](../../modules/chess-api/src/main/scala/de/eljachess/chess/api/health/BotServiceHealthCheck.scala) | Readiness-Probe: pingt bot-service an |

### Geänderte Datei

| Datei | Was geändert wurde |
|-------|-----------|
| [`modules/chess-api/src/main/scala/de/eljachess/chess/api/service/GameService.scala`](../../modules/chess-api/src/main/scala/de/eljachess/chess/api/service/GameService.scala) | Bot-Logik entfernt, BotClient per CDI injiziert, `applyBotMoveIfNeeded` als privater Hook |

### Relevante Codestellen

**`BotClient.scala` — Fault Tolerance Stack (Zeilen 44–48)**
```scala
@Retry(maxRetries = 1, delay = 300L)           // 1x wiederholen bei Netzwerkfehler
@CircuitBreaker(requestVolumeThreshold = 4,    // nach 75% Fehlern: Circuit öffnet
                failureRatio = 0.75,
                delay = 10000L)                // 10s warten, dann halboffener Test
@Timeout(3000L)                                // nach 3s abbrechen
@Fallback(fallbackMethod = "fetchMoveFallback") // wenn alles fehlschlägt: None
def fetchMove(fen: String, color: String, elo: Int): Option[(String, String)]
```

**`GameService.scala` — Bot-Zug nach jedem Spielerzug (Zeilen 173–182)**
```scala
private def applyBotMoveIfNeeded(gameId: String, manager: GameManager): Unit =
  for config <- botConfigs.get(gameId) do
    val ctrl = manager.state
    if ctrl.currentTurn == config.botColor then
      botClient.fetchMove(fen, colorStr, config.elo) match
        case Some((from, to)) => manager.move(s"$from $to")
        case None             => ()   // Service nicht erreichbar → Spiel läuft weiter
```

**`BotServiceHealthCheck.scala` — Dependency Health Check (Zeilen 17–54)**
```scala
@Readiness
@ApplicationScoped
class BotServiceHealthCheck extends HealthCheck:
  override def call(): HealthCheckResponse =
    // GETs bot-service:8081/q/health/ready
    // → UP wenn 200 OK, sonst DOWN mit Fehlerdetails
```

---

## 3. Docker-Orchestrierung

### Geänderte Datei

| Datei | Was geändert wurde |
|-------|-----------|
| [`docker-compose.yml`](../../docker-compose.yml) | Beide Services + Health Check Dependency |

### Relevante Codestellen

**`docker-compose.yml` — Startup-Reihenfolge (Zeilen 55–63)**
```yaml
game-service:
  depends_on:
    bot-service:
      condition: service_healthy   # game-service startet erst wenn bot-service UP ist
  environment:
    BOT_SERVICE_URL: http://bot-service:8081   # Docker-internes DNS
```

---

## 4. Konfiguration

| Datei | Was konfiguriert wird |
|-------|-----------|
| [`modules/chess-api/src/main/resources/application.properties`](../../modules/chess-api/src/main/resources/application.properties) | `bot-service.url`, CORS, OTEL, Prometheus |
| [`modules/bot-service/src/main/resources/application.properties`](../../modules/bot-service/src/main/resources/application.properties) | Port 8081, OTEL, Prometheus |

**Environment Variable Override:**  
`bot-service.url=http://localhost:8081` wird in Docker zu `BOT_SERVICE_URL=http://bot-service:8081`  
(MicroProfile Config-Konvention: Punkt → Underscore, Großschreibung)

---

## 5. Observability (automatisch aktiv)

| Endpoint | Was es zeigt |
|----------|-------------|
| `http://localhost:8080/q/health` | Health-Status inkl. bot-service Erreichbarkeit |
| `http://localhost:8080/q/metrics` | Prometheus-Metriken (HTTP-Requests, JVM, etc.) |
| `http://localhost:8080/q/swagger-ui` | OpenAPI-Dokumentation game-service |
| `http://localhost:8081/q/swagger-ui` | OpenAPI-Dokumentation bot-service |

---

## 6. Tests

| Datei | Was getestet wird |
|-------|-------------------|
| [`modules/chess-api/src/test/scala/.../health/BotServiceHealthCheckSpec.scala`](../../modules/chess-api/src/test/scala/de/eljachess/chess/api/health/BotServiceHealthCheckSpec.scala) | Health Check gibt DOWN zurück wenn bot-service nicht erreichbar |
| [`modules/chess-api/src/test/scala/.../service/GameServiceSpec.scala`](../../modules/chess-api/src/test/scala/de/eljachess/chess/api/service/GameServiceSpec.scala) | Game-Service Unit Tests (ohne echten bot-service) |
| [`modules/bot-service/src/test/scala/.../BotEngineSpec.scala`](../../modules/bot-service/src/test/scala/de/eljachess/botservice/BotEngineSpec.scala) | Bot-Engine Unit Tests |

**Tests laufen:**
```bash
./gradlew :modules:chess-api:test
./gradlew :modules:bot-service:test
```

---

## 7. Architektur-Dokumentation

| Datei | Was es enthält |
|-------|---------------|
| [`docs/adr/ADR-001-microservice-architecture.md`](../adr/ADR-001-microservice-architecture.md) | Warum wurde aufgeteilt? Vorteile/Nachteile |
| [`docs/adr/ADR-002-sync-rest-resilience.md`](../adr/ADR-002-sync-rest-resilience.md) | Warum synchrones REST statt async? |
| [`docs/readme/microservice-approach-a.md`](microservice-approach-a.md) | Vorher/Nachher Architekturdiagramm |

---

## Kurzfassung: Was wurde NEU gebaut

1. **`modules/bot-service/`** — komplett neues Gradle-Modul / Quarkus-Service
2. **`BotClient.scala`** — HTTP-Client mit `@Retry`, `@CircuitBreaker`, `@Timeout`, `@Fallback`
3. **`BotServiceHealthCheck.scala`** — game-service pingt bot-service an und meldet DOWN wenn nicht erreichbar
4. **`GameService.scala`** — `applyBotMoveIfNeeded()` ruft BotClient nach jedem Zug auf
5. **`docker-compose.yml`** — `depends_on: service_healthy` sichert die Startreihenfolge
