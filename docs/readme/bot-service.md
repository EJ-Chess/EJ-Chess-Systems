# Bot-Service – KI-Engine für Schachzüge

Der **Bot-Service** ist ein unabhängiger Quarkus REST-Microservice, der Schachzüge mit KI berechnet.  
Er läuft auf **Port 8081** und ist **vollständig stateless** — jeder Request ist in sich geschlossen.

---

## Überblick

| Aspekt | Details |
|--------|---------|
| **Sprache** | Scala 3 |
| **Framework** | Quarkus 3.25 + Jakarta REST |
| **Port** | 8081 |
| **Zweck** | Zugberechnung via REST-API (Greedy-Random-Algorithmus) |
| **Abhängigkeiten** | `chess-core` (Board, Moves, GameState) |

---

## Architektur

```
BotResource (REST-Endpoints)
    ↓
BotEngine (Zugberechnung, ELO-gesteuert)
    ↓
BotStreamProcessor (Reactive Streams für Batch-Verarbeitung)
    ↓
chess-core (Board-Logik, Move-Validierung)
```

### Komponenten

- **BotResource**: REST-Endpoints (`POST /move`, `POST /moves`, Health-Check)
- **BotEngine**: Greedy-Random-Algorithmus mit ELO-Rating zur Zugwahl-Steuerung
- **BotStreamProcessor**: Verarbeitet Züge mit Reactive Streams (Pekko)
- **DTOs**: `BotMoveRequest` (Board + Spieler), `BotMoveResponse` (Zug + Info)

---

## API-Endpoints

Swagger UI: http://localhost:8081/q/swagger-ui

### POST `/move`
Berechnet einen einzelnen Schachzug.

**Request:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "color": "BLACK"
}
```

**Response:**
```json
{
  "move": "e7e5",
  "confidence": 0.85,
  "evaluationScore": 0.1
}
```

### POST `/moves`
Berechnet mehrere Züge parallel (Batch-Verarbeitung über Reactive Streams).

**Request:**
```json
{
  "requests": [
    { "fen": "...", "color": "BLACK" },
    { "fen": "...", "color": "BLACK" }
  ]
}
```

### GET `/q/health`
Health-Check für Kubernetes/Container-Orchestrierung.

---

## Starten

### Lokal (Dev-Modus)
```bash
./gradlew :modules:bot-service:quarkusDev
```
Service läuft dann unter http://localhost:8081

### Docker
```bash
# Schritt 1: JAR bauen
./gradlew :modules:bot-service:quarkusBuild

# Schritt 2: Mit docker-compose
docker-compose up bot-service
```

---

## Tests

```bash
# Unit Tests (BotEngine-Logik)
./gradlew :modules:bot-service:test -k "BotEngineSpec"

# Integration Tests (REST-API)
./gradlew :modules:bot-service:test -k "BotResourceIT"

# Alle Tests
./gradlew :modules:bot-service:test
```

---

## Abhängigkeiten

- **chess-core**: Board-Modell, Fen-Parser, Move-Validierung
- **Pekko Streams**: Reactive Streams für Batch-Verarbeitung
- **Jackson**: JSON-Serialisierung (mit Scala-Modul)
- **Quarkus**: HTTP-Server, Health-Checks, OpenAPI/Swagger

---

## Besonderheiten

### Stateless Design
Der Bot-Service speichert **keine Spielzustände**. Jeder Request ist völlig unabhängig.  
Board-Status wird als **FEN-String** vom Game-Service übergeben.

### ELO-Steuerung
Der Bot nutzt ein **ELO-Rating-System**, um die Spielstärke zu kontrollieren:
- **Höheres ELO** → Bessere Zug-Qualität (tiefere Analyse)
- **Niedrigeres ELO** → Zufälligere Züge (stärkere Varianz)

### Reactive Streams
Batch-Anfragen werden mit **Apache Pekko Streams** verarbeitet — asynchrone Zugberechnung für mehrere Positionen parallel.

---

## Integration mit dem Game-Service

Der Game-Service (`modules/chess-api`) ruft den Bot-Service auf:

```scala
// Game-Service nutzt Bot-Service
POST http://localhost:8081/move
{
  "fen": "<aktueller Board-Status>",
  "color": "BLACK"
}
```

Beim Starten des Game-Services wird ein **Health-Check** durchgeführt:  
Antwortet der Bot-Service nicht, bleibt die Bot-Spielseite in der Web-UI "hängen".

---

## Konfiguration

Wichtige Quarkus-Eigenschaften (in `application.yml`):

```yaml
quarkus:
  http:
    port: 8081
  smallrye-openapi:
    info-title: "Bot Service API"
```

---

## OpenAPI-Dokumentation

Vollständige API-Dokumentation (Swagger):  
http://localhost:8081/q/swagger-ui

OpenAPI-JSON:  
http://localhost:8081/q/openapi

---

## Weitere Ressourcen

- [Hauptprojekt-README](../../README.md) — Alle Startszenarien (Dev, Docker, Kubernetes)
- [Deployment](deployment.md) — Docker & Kubernetes Deployment
- [Microservices Dokumentation](microservices.md)
- [Resilience-Pattern](resilience.md) — Health-Checks, Circuit Breaker
