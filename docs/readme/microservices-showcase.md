# Microservices — Code-Showcase

Was zu zeigen wenn gefragt wird: *"Wie sieht denn Microservices bei euch aus?"*

---

## 1. Service-Grenzen: game-service ↔ bot-service

### Das Problem vorher
```scala
// Monolith — alles in einem Prozess
val game = GameFactory.createGame(white = player, black = GreedyRandomBot(elo=1400))
val move = game.applyMove(playerMove)
// Bot läuft inline, blockiert, kostet JVM-Memory
```

### Jetzt — Microservices
```scala
// game-service (Port 8080) → HTTP → bot-service (Port 8081)
// Nur wenn Bot am Zug ist und bot-service verfügbar
if shouldApplyBotMove then
  val move = botClient.fetchMove(fen, "black", 1400)
```

---

## 2. Resilience: Service-Ausfälle abfedern

### Health Check — Bot-Service Erreichbarkeit prüfen
**Datei:** `modules/chess-api/src/main/scala/de/eljachess/chess/api/health/BotServiceHealthCheck.scala`

```scala
@Readiness
@ApplicationScoped
class BotServiceHealthCheck extends HealthCheck:

  protected def targetUrl: String =
    try
      org.eclipse.microprofile.config.ConfigProvider
        .getConfig()
        .getOptionalValue("bot-service.url", classOf[String])
        .orElse("http://localhost:8081")
    catch
      case _: Exception => "http://localhost:8081"

  private val httpClient = HttpClient.newHttpClient()

  override def call(): HealthCheckResponse =
    try
      val response = httpClient.send(
        HttpRequest.newBuilder()
          .uri(URI.create(s"$targetUrl/q/health/ready"))
          .GET()
          .build(),
        HttpResponse.BodyHandlers.ofString()
      )
      if response.statusCode() == 200 then
        HealthCheckResponse.up("bot-service")
      else
        HealthCheckResponse
          .named("bot-service")
          .down()
          .withData("httpStatus", response.statusCode().toLong)
          .build()
    catch
      case ex: Exception =>
        HealthCheckResponse
          .named("bot-service")
          .down()
          .withData("error", ex.getMessage)
          .build()
```

**Ergebnis:**
```bash
# game-service Health-Check
$ curl http://localhost:8080/q/health/ready
{
  "status": "UP",
  "checks": [
    {
      "name": "bot-service",
      "status": "UP"
    }
  ]
}
```

Docker-Compose: game-service startet **nicht**, wenn bot-service nicht healthy ist.

---

## 3. Fault Tolerance: Retry + Circuit Breaker

**Datei:** `modules/chess-api/src/main/scala/de/eljachess/chess/api/client/BotClient.scala`

```scala
@Retry(maxRetries = 1, delay = 300L)
@CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000L)
@Timeout(3000L)
@Fallback(fallbackMethod = "fetchMoveFallback")
def fetchMove(fen: String, color: String, elo: Int): Option[(String, String)] =
  // HTTP POST an bot-service
  val response = client
    .target(botServiceUrl)
    .path("/bot/move")
    .request(MediaType.APPLICATION_JSON)
    .post(Entity.json(BotRequest(fen, color, elo)))
    .readEntity(classOf[BotResponse])
  Some((response.from, response.to))

private def fetchMoveFallback(fen: String, color: String, elo: Int): Option[(String, String)] =
  None  // Spiel läuft weiter, Bot macht keinen Zug
```

**Wie es wirkt:**
- **Retry:** Einmal wiederholen (kurze Netzwerk-Blips)
- **Circuit Breaker:** Nach 3+ Fehlern (75% Fehlerrate) → **offen** für 10s, dann wieder testen
- **Timeout:** Nach 3s abbrechen (verhindert Hänger)
- **Fallback:** Wenn alles fehlschlägt → `None` (Spiel läuft ohne Bot-Zug)

---

## 4. API-Verträge: REST zwischen Services

### Bot-Service Endpoint
**Datei:** `modules/bot-service/src/main/scala/de/eljachess/chess/bot/api/BotResource.scala`

```scala
@Path("/bot")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class BotResource:

  @POST
  @Path("/move")
  def getMove(request: BotRequest): BotResponse =
    val engine = BotEngine()
    val (from, to) = engine.findMove(request.fen, request.color, request.elo)
    BotResponse(from, to)

case class BotRequest(fen: String, color: String, elo: Int)
case class BotResponse(from: String, to: String)
```

**Swagger (Dokumentation):**
```
POST http://localhost:8081/bot/move
Content-Type: application/json

{
  "fen":   "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "color": "black",
  "elo":   1400
}

→ 200 OK
{
  "from": "e7",
  "to":   "e5"
}
```

Beide Services haben ihre eigene OpenAPI/Swagger unter `/q/swagger-ui`.

---

## 5. Deployment: Docker-Compose mit Health Checks

**Datei:** `docker-compose.yml`

```yaml
services:

  bot-service:
    build: modules/bot-service
    ports:
      - "8081:8081"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8081/q/health/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 40s

  game-service:
    build: modules/chess-api
    ports:
      - "8080:8080"
    depends_on:
      bot-service:
        condition: service_healthy  # ← game-service wartet bis bot-service healthy ist
    environment:
      BOT_SERVICE_URL: http://bot-service:8081  # Docker DNS
```

**Startup-Reihenfolge:**
1. Bot-Service baut & startet
2. Game-Service wartet auf `bot-service:8081/q/health/ready` == 200 OK
3. Erst dann startet Game-Service
4. Web-UI wartet auf Game-Service

---

## 6. Metriken: Was läuft? Wie schnell?

**Prometheus Metriken (alle 30s exportiert):**

```bash
$ curl http://localhost:8080/q/metrics

# Auf game-service:
http_server_requests_seconds_bucket{endpoint="/bot/move",method="POST",status="200",...} 0.235
http_server_requests_seconds_bucket{endpoint="/bot/move",method="POST",status="500",...} 0.045
http_client_requests_seconds_bucket{status="503"} 0.015

# Circuit Breaker Status:
resilience4j_circuitbreaker_state{name="fetchMove",state="OPEN"} 1.0   # offen (Fehler)
resilience4j_circuitbreaker_state{name="fetchMove",state="CLOSED"} 0.0
```

Mit Grafana könnte man diese Kurven visualisieren.

---

## 7. Testbarkeit durch Grenzen

### Service-Test: health check ohne running bot-service

**Datei:** `modules/chess-api/src/test/scala/de/eljachess/chess/api/health/BotServiceHealthCheckSpec.scala`

```scala
class BotServiceHealthCheckSpec extends AnyFlatSpec with Matchers:

  // Subclass mit garantiert unerreichbarer URL
  private class IsolatedCheck extends BotServiceHealthCheck:
    override protected def targetUrl: String = "http://localhost:1"

  "BotServiceHealthCheck" should "return DOWN when unreachable" in {
    val check = new IsolatedCheck()
    check.call().getStatus shouldBe HealthCheckResponse.Status.DOWN
  }
```

**Tests:**
- Laufen **isoliert**, keine echte Netzwerk-Abhängigkeit
- Game-Service Tests brauchen Bot-Service **nicht** zu mocken — nehmen Fallback-Path

### Integration Test: beide Services zusammen

```bash
./gradlew :modules:chess-api:test --tests "*GameServiceSpec*"
# Startet beide Services via @QuarkusTest, testet echte HTTP-Aufrufe
```

---

## Zusammenfassung zum Zeigen

| Feature | Code-Datei | Zeigen |
|---------|-----------|--------|
| **Service-Aufteilung** | `modules/{bot-service,chess-api}` | 2 eigenständige Gradle-Module, unterschiedliche Ports |
| **API-Vertrag** | `BotResource.scala` + `BotRequest/Response` | REST POST, JSON-Schema |
| **Resilience** | `BotClient.scala` mit `@Retry`, `@CircuitBreaker` | Fault-Tolerance Annotations + Fallback |
| **Health Checks** | `BotServiceHealthCheck.scala` | Dependency-Probing zwischen Services |
| **Testbarkeit** | `BotServiceHealthCheckSpec.scala` | Subclass-Override ohne echte Netzwerk |
| **Deployment** | `docker-compose.yml` | `depends_on: service_healthy` |
| **Observability** | `/q/metrics`, `/q/health`, `/q/swagger-ui` | Standard-Endpoints (MicroProfile) |

---

## Gespräche dazu

**"Warum separate Services?"**
- Unabhängig deploybar: Bot-Algorithm-Update brauchts game-service nicht zu stoppen
- Skalierbar: Bot-Service kann horizontal (mehrere Instanzen) skaliert werden
- Ausfallsicher: Game bleibt spielbar wenn bot-service temporär down ist

**"Overhead? Nicht zu langsam?"**
- HTTP-Aufruf bot-service: ~50-200ms (vs. Inline: <1ms)
- Acceptable für strategisches Spiel (nicht für Echtzeit-Gaming)
- MicroProfile Fault Tolerance verhindert cascading failures

**"Wie wird das getestet?"**
- Jeder Service separat unit/integration tests
- Docker-Compose für End-to-End
- Health Checks sichern Startup-Reihenfolge ab
