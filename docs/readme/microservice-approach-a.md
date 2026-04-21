# Microservice-Architektur — Branch `feature/microservice-approach-a`

Dieser Branch führt eine Microservice-Aufteilung ein.  
Ausgangspunkt war `feature/web-ui-approach-a` (inkl. Bot-Merge aus `feature/bot-implementation`).

---

## Was wurde geändert

### Neu: `modules/bot-service`

Ein eigenständiger Quarkus-Dienst, der ausschließlich für KI-Zugberechnung zuständig ist.

**Endpoint:**
```
POST /bot/move
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

**Eigenschaften:**
- Läuft auf **Port 8081**
- Vollständig **stateless** — kein Session-State, kein Storage
- Hängt nur von `:core` ab (kein JavaFX, kein chess-bot)
- ELO steuert Spielstärke: ≥1800 → Top-1-Zug, ≥1400 → Top-3, <1400 → Top-5

---

### Geändert: `modules/chess-api` (Game-Service)

| Vorher | Nachher |
|--------|---------|
| Direkte Gradle-Abhängigkeit auf `chess-bot` | Keine `chess-bot`-Abhängigkeit mehr |
| `GameFactory.createGame()` instanziiert Bot inline | `GameController` immer ohne Bot erstellt |
| JavaFX/ScalaFX als Dependency | JavaFX/ScalaFX entfernt |
| `LocalUIStartup.scala` startet Desktop-GUI mit | Datei entfernt |
| Bot-Zug läuft im selben Prozess | Bot-Zug via HTTP-Call an `bot-service` |

**Bot-Konfiguration** wird jetzt pro Spiel in einem `TrieMap[String, BotConfig]` gespeichert:
```scala
case class BotConfig(botColor: Color, elo: Int)
```

Nach jedem Spielerzug prüft `applyBotMoveIfNeeded`, ob der Bot am Zug ist, und ruft dann `POST /bot/move` auf.  
Ist der Bot-Service nicht erreichbar, wird der Zug still übersprungen — das Spiel bleibt spielbar.

---

### Entfernt: `LocalUIStartup.scala`

Diese Datei startete beim Hochfahren des Game-Service automatisch die JavaFX-GUI.  
In einer Microservice-Architektur hat Desktop-GUI-Startup nichts in einem REST-Dienst zu suchen.  
Die JavaFX-GUI ist weiterhin über `modules/chess-bot` erreichbar.

---

### `settings.gradle.kts`

```kotlin
include(":modules:bot-service")   // neu hinzugefügt
```

---

## Architektur im Vergleich

### Vorher — Modularer Monolith

```
Browser / chess-ui  (React, Port 5173)
        │
        │  HTTP REST /games/*
        ▼
  chess-api  (Port 8080)
        │
        ├── GameService
        │     └── GameFactory.createGame()
        │           └── GreedyRandomBot  ◄──┐ selber Prozess
        │                                    │ selbe JVM
        ├── LocalUIStartup                   │ direkter Aufruf
        │     └── GameSetupScene (JavaFX) ───┘
        │
        │  compile-time (Gradle-Dependency)
        ▼
  chess-bot  (GreedyRandomBot + JavaFX-GUI)
        │
        │  compile-time
        ▼
  core  (Schach-Engine)
```

**Problem:** Bot-Logik, Desktop-GUI-Startup und REST-API waren im selben Prozess
gebündelt und konnten nicht unabhängig skaliert oder deployed werden.

---

### Nachher — Microservices

```
Browser / chess-ui  (React, Port 5173)
        │
        │  HTTP REST /games/*
        ▼
  game-service  (chess-api, Port 8080)
        │
        │  HTTP POST /bot/move
        │  (nur wenn Gegner = Bot und Bot am Zug)
        ▼
  bot-service  (Port 8081)
        │
        │  compile-time
        ▼
  core  (Schach-Engine, kein eigener Dienst)
```

**Ergebnis:** Jeder Dienst ist unabhängig deploybar, skalierbar und testbar.  
`chess-bot` bleibt als eigenständiger Desktop-Client erhalten.

---

## Deployment: beide Services starten

```bash
# Terminal 1
./gradlew :modules:chess-api:quarkusDev

# Terminal 2
./gradlew :modules:bot-service:quarkusDev

# Terminal 3 (optional – Web-UI)
cd modules/chess-ui && npm run dev
```

Ohne laufenden Bot-Service können nur Mensch-gegen-Mensch-Partien gespielt werden.  
Bot-Partien laufen dann ohne automatischen Gegenzug.

---

## Tests

```bash
./gradlew :modules:bot-service:test     # 12 Tests — BotEngineSpec
./gradlew :modules:chess-api:test       # 38 Tests — GameServiceSpec
./gradlew :modules:chess-ui:npmTest     # 48 Tests — Web-UI
```

---

## Weiterführend

- Architekturentscheidung: [docs/adr/ADR-001-microservice-architecture.md](../adr/ADR-001-microservice-architecture.md)
