# Resilience — Health Checks, Circuit Breaker & OpenAPI

Dieses Dokument beschreibt die Maßnahmen, die EJa Chess gegen Ausfälle und
unerwartetes Verhalten absichern.

---

## Health Checks (`quarkus-smallrye-health`)

Beide Quarkus-Dienste stellen automatisch drei Endpunkte bereit:

| Endpunkt | Bedeutung |
|----------|-----------|
| `/q/health` | Aggregierter Status (live + ready) |
| `/q/health/live` | Liveness — ist der Prozess am Leben? |
| `/q/health/ready` | Readiness — kann der Dienst Requests annehmen? |

### Beispiel-Antwort

```bash
curl http://localhost:8080/q/health/ready
```

```json
{
  "status": "UP",
  "checks": [
    { "name": "SmallRye Reactive Messaging - readiness check", "status": "UP" }
  ]
}
```

### Verwendung in docker-compose

docker-compose nutzt `/q/health/ready` als Startbedingung:

```yaml
depends_on:
  bot-service:
    condition: service_healthy
```

Der Game-Service startet erst, wenn der Bot-Service seine Readiness-Probe besteht.

---

## Circuit Breaker + Fallback (`quarkus-smallrye-fault-tolerance`)

Der Game-Service kommuniziert mit dem Bot-Service über HTTP.
Um einen Kaskadenausfall zu verhindern, ist der HTTP-Call in `BotClient` durch
drei MicroProfile-Fault-Tolerance-Mechanismen geschützt:

```
Aufruf → @Timeout(3 s) → @CircuitBreaker → HTTP POST /bot/move
                                          ↓ (Fehler oder Timeout)
                                    @Fallback → None
```

### `BotClient.fetchMove` — Annotation-Übersicht

```scala
@CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000L)
@Timeout(3000L)
@Fallback(fallbackMethod = "fetchMoveFallback")
def fetchMove(fen: String, color: String, elo: Int): Option[(String, String)]
```

| Annotation | Konfiguration | Wirkung |
|------------|---------------|---------|
| `@Timeout` | 3 000 ms | Bricht den HTTP-Call nach 3 Sekunden ab |
| `@CircuitBreaker` | Schwellenwert: 4 Requests, 75 % Fehlerquote, 10 s Wartezeit | Öffnet den Circuit nach ≥ 3 Fehlern in 4 Calls; hält ihn 10 s offen (kein weiterer HTTP-Call) |
| `@Fallback` | `fetchMoveFallback` | Gibt `None` zurück — das Spiel läuft ohne Bot-Antwort weiter |

### Verhalten aus Sicht des Spielers

| Szenario | Ergebnis |
|----------|----------|
| Bot-Service antwortet normal | Zug wird berechnet und angewendet |
| Bot-Service ist langsam (> 3 s) | `@Timeout` greift — Bot-Zug wird übersprungen |
| Bot-Service antwortet mit Fehler | `@CircuitBreaker` zählt Fehler — nach Schwellenwert kein weiterer Versuch |
| Circuit offen | Sofortige `@Fallback`-Antwort — kein Warten |
| Mensch-gegen-Mensch-Partie | `BotClient` wird gar nicht aufgerufen |

Das Spiel **bleibt immer spielbar** — Bot-Ausfälle blockieren keine Partien.

---

## OpenAPI / Swagger UI (`quarkus-smallrye-openapi`)

Beide Dienste generieren ihre API-Dokumentation automatisch aus den JAX-RS-Annotations.

| Dienst | Swagger UI | OpenAPI YAML |
|--------|-----------|--------------|
| Game-Service | http://localhost:8080/q/swagger-ui | http://localhost:8080/q/openapi |
| Bot-Service | http://localhost:8081/q/swagger-ui | http://localhost:8081/q/openapi |

Die Dokumentation ist auch in Docker verfügbar und bleibt immer synchron mit dem Code —
kein separater Pflegeaufwand.

### Konfiguration (application.properties)

```properties
quarkus.swagger-ui.always-include=true
quarkus.smallrye-openapi.info-title=EJa Chess — Game Service
quarkus.smallrye-openapi.info-version=1.0.0
```

---

## Zusammenhang der Maßnahmen

```
docker-compose
  └─ Health Check /q/health/ready
       └─ Startreihenfolge: bot-service → game-service → chess-ui

game-service (HTTP-Call zu bot-service)
  └─ BotClient.fetchMove
       ├─ @Timeout(3 s)          — kein Hänger
       ├─ @CircuitBreaker        — kein Kaskadenausfall
       └─ @Fallback → None       — Spiel läuft immer weiter
```

---

## Weiterführend

- Architekturentscheidung: [docs/adr/ADR-002-sync-rest-resilience.md](../adr/ADR-002-sync-rest-resilience.md)
- Docker-Setup: [docs/readme/docker.md](docker.md)
