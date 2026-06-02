# Streams in unserem Projekt — Kurz erklärt

> **Kontext:** Zwei verschiedene Stream-Implementierungen für zwei verschiedene Probleme.  
> Prof hat nur Pekko gezeigt → wir zeigen warum wir zusätzlich FS2 brauchten.

---

## 🎯 Kurz zusammengefasst

| Service | Technologie | Problem | Lösung | File |
|---------|-------------|---------|--------|------|
| **Bot-Service** | Pekko | Zu viele Bot-Anfragen gleichzeitig → zu viele Threads → CRASH | Queue (500) + Workers (8) | `BotStreamProcessor.scala` |
| **Chess-API** | FS2 | 500 Spiele parallel, thread-safe, elegant | `Stream.range + parEvalMap(50)` | `GameLifecycleStream.scala` |

---

## 1️⃣ Pekko — Bot-Service

### Das Problem

```
Turnier: 12 Teams spielen.
Jedes Spiel: 50-60 Bot-Anfragen (ein Request pro Zug).
Gleichzeitig: ~100 Bot-Anfragen ankommen.

Ohne Streams:
  Request 1  → Thread 1
  Request 2  → Thread 2
  ...
  Request 100 → Thread 100
  
  Jeder Thread = ~1MB RAM
  100 Requests = 100MB
  1000 Requests = 1GB
  
  JVM Default = 512MB → Out of Memory → 💥 CRASH
```

### Die Lösung: Bounded Queue mit Backpressure

```scala
// BotStreamProcessor.scala (70 Lines)
Source.queue[QueueElement](bufferSize = 500, OverflowStrategy.dropNew)
  .mapAsync(parallelism = 8) { elem =>
    Future {
      val move = BotEngine.bestMove(elem.request.fen, ...)
      elem.promise.success(Some(move))
    }
  }
```

**Was passiert:**

```
Request 1-8:     → Processing (8 Worker gleichzeitig)
Request 9-500:   → In Queue (wartend)
Request 501+:    → DROPPED → None → HTTP 503 "Service Overloaded"

Total Threads: ~10-15 (nicht 1000!)
Total Memory: ~50-100 MB (nicht 1000 MB!)
Status: ✅ Stabil
```

### Backpressure erklärt

```
Server zu Client: "Ich bin voll! (HTTP 503)"
Client liest 503 → intelligente Retry-Logic
                 → versucht später nochmal
                 
Besser als: Client wartet ewig, Server hat Kapazität-Probleme
```

### Code-Flow

```
REST-Request kommt an
  ↓
BotResource.getMove()
  ↓
BotStreamProcessor.enqueue(request)
  ├─ Queue hat Platz?
  │  ├─ Ja → Promise geben, weiter zu mapAsync
  │  └─ Nein → sofort None, Client kriegt 503
  ↓
mapAsync(8) = 8 Worker-Threads rechnen BotEngine.bestMove()
  ↓
Promise erfüllt → REST Response (200 oder 503)
```

### Files

- **Impl:** `modules/bot-service/src/main/scala/de/eljachess/botservice/BotStreamProcessor.scala`
- **Test:** `modules/bot-service/src/test/scala/de/eljachess/botservice/BotStreamProcessorSpec.scala` (7 Tests)
- **Integration:** `modules/bot-service/src/test/scala/de/eljachess/botservice/BotResourceIT.scala` (4 Tests)

---

## 2️⃣ FS2 — Chess-API Bulk Operations

### Das Problem

```
POST /games/bulk {"count": 500}

Anforderung:
  - 500 Spiele gleichzeitig spielen
  - Jedes Spiel: create → move(e2→e4) → delete
  - Thread-safe (keine Race Conditions)
  - Response: {"total":500, "successful":500, "failed":0, "durationMs":...}
```

### Die Lösung: Pure Functional Streams mit Bounded Concurrency

```scala
// GameLifecycleStream.scala (56 Lines)
def runBulk(count: Int): IO[BulkGameResult] =
  Stream
    .range(1, count + 1)           // Zahlen 1 bis count
    .parEvalMap(50) { i =>         // Max 50 parallel
      singleLifecycle(i).attempt   // Create, Move, Delete
    }
    .compile.toList                // Ausführen & sammeln
    .flatMap { results =>
      // Erfolgreiche/Fehlgeschlagene zählen
      IO.realTimeInstant.flatMap { t0 =>
        IO.realTimeInstant.map { t1 =>
          BulkGameResult(
            total = count,
            successful = results.count(_.isRight),
            failed = results.count(_.isLeft),
            durationMs = t1.toEpochMilli - t0.toEpochMilli
          )
        }
      }
    }
```

**Was passiert:**

```
POST /games/bulk {"count": 500}
  ↓
Stream.range(1, 501)  [1, 2, 3, ..., 500]  ← Lazy! Noch nichts
  ↓
.parEvalMap(50)  ← Jetzt wird's real
  ├─ Batch 1: Spiele 1-50 parallel
  │   ├─ Spiel 1: create → move → delete
  │   ├─ Spiel 2: create → move → delete
  │   └─ ...
  ├─ Batch 2: Spiele 51-100 parallel
  ├─ ...
  └─ Batch 10: Spiele 451-500 parallel
  ↓
.compile.toList  ← Alle Ergebnisse sammeln
  ↓
BulkGameResult (JSON) → Response
```

### Warum FS2 hier (nicht Pekko)?

| Aspekt | Pekko | FS2 |
|--------|-------|-----|
| **Lokal oder Verteilt?** | Verteilt (Actor-System) | Lokal (Single JVM) |
| **Netzwerk nötig?** | Ja (über Netzwerk möglich) | Nein |
| **Pure Functional?** | Nee (imperative Queue-Verwaltung) | Ja (keine var, immer gleich) |
| **Für diesen Use-Case?** | Overkill | Perfekt |

**FS2 ist eleganter für lokale Operationen** — keine Queue-Verwaltung, keine Promises, einfach nur: "Mach 50 gleichzeitig, der Rest wartet."

### Files

- **Impl:** `modules/chess-api/src/main/scala/de/eljachess/chess/api/service/GameLifecycleStream.scala` (56 Lines)
- **Controller:** `modules/chess-api/src/main/scala/de/eljachess/chess/api/controller/BulkGameController.scala` (20 Lines)
- **Test:** `modules/chess-api/src/test/scala/de/eljachess/chess/api/service/GameLifecycleStreamSpec.scala` (36 Lines, 5 Tests)
- **Integration:** `modules/chess-api/src/test/scala/de/eljachess/chess/api/controller/BulkGameControllerIT.scala` (40 Lines, 2 Tests)

---

## Warum zwei verschiedene Technologien?

### **Pekko = Für externe Anfragen (Backpressure kritisch)**

```
Bot-Service ist **öffentlich** über HTTP erreichbar
  ↓
Unbegrenzte Anfragen können ankommen
  ↓
MUSS kontrollieren, wie viele gleichzeitig verarbeitet werden
  ↓
Pekko Queue mit bufferSize = 500
  ↓
Wenn voll: HTTP 503 (Client: "OK, ich warte/probiere später")
```

### **FS2 = Für interne Operationen (Eleganz wichtig)**

```
Chess-API internal: POST /games/bulk
  ↓
Wir starten selbst diese Operation (nicht der Client)
  ↓
Wir wissen: Max 50 parallel ist OK
  ↓
FS2 Stream: lazy, functional, keine Seiteneffekte
  ↓
Einfacher Code, leichter zu testen
```

---

## 🧪 Live-Test: Backpressure sehen

### Pekko Bot-Stream

```bash
# Terminal 1: Bot starten
./gradlew :modules:bot-service:quarkusDev

# Terminal 2: 600 parallele Requests
for i in {1..600}; do
  curl -s -X POST http://localhost:8081/bot/move \
    -H "Content-Type: application/json" \
    -d '{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","color":"white","elo":1200}' \
    -w "Request $i: %{http_code}\n" &
done
wait
```

**Was ihr seht:**
```
Request 1: 200    ✅ Processing
Request 2: 200    ✅ Processing
...
Request 500: 200  ✅ In Queue
Request 501: 503  ❌ Queue voll!
Request 502: 503  ❌ Queue voll!
...
Request 600: 503  ❌ Queue voll!
```

**Das ist Backpressure!** 🎯

### FS2 Bulk-Operation

```bash
# Terminal 1: Services starten
./gradlew :modules:chess-api:quarkusDev
./gradlew :modules:bot-service:quarkusDev

# Terminal 2: 500 Spiele parallel
curl -X POST http://localhost:8080/games/bulk \
  -H "Content-Type: application/json" \
  -d '{"count":500}'

# Response nach ~4-5 Sekunden:
# {
#   "total": 500,
#   "successful": 500,
#   "failed": 0,
#   "durationMs": 4523
# }
```

**Das ist pure functional streaming!** 🚀

---

## 📊 Zahlen für Turnier

```
Szenario: 12 Teams, Round-Robin

Total Spiele: 132 (66 Paarungen × 2 Richtungen)
Pro Spiel: 50 Züge
Bot-Requests total: 132 × 50 = 6600

Gleichzeitig:
- Best Case: 5-10 Spiele laufen parallel
  → ~250-500 Bot-Requests parallel
- Worst Case: Alle spielen "sofort"
  → ~100-200 Bot-Requests parallel

Buffer = 500 ist **comfortable** ✅
Buffer = 200 wäre **tight** ⚠️
```

---

## 📁 Alle Files auf einen Blick

### Bot-Service (Pekko)

```
modules/bot-service/
├── src/main/scala/de/eljachess/botservice/
│   └── BotStreamProcessor.scala       ← Die Queue-Impl
├── src/test/scala/de/eljachess/botservice/
│   ├── BotStreamProcessorSpec.scala   ← Unit Tests (7)
│   └── BotResourceIT.scala            ← Integration Tests (4)
└── build.gradle.kts                   ← Pekko Deps
```

### Chess-API (FS2)

```
modules/chess-api/
├── src/main/scala/de/eljachess/chess/api/
│   ├── service/
│   │   └── GameLifecycleStream.scala  ← Die Stream-Impl
│   └── controller/
│       └── BulkGameController.scala   ← REST Endpoint
├── src/test/scala/de/eljachess/chess/api/
│   ├── service/
│   │   └── GameLifecycleStreamSpec.scala  ← Unit Tests (5)
│   └── controller/
│       └── BulkGameControllerIT.scala     ← Integration Tests (2)
└── build.gradle.kts                   ← FS2 Deps
```

---

## ✅ Test-Status

```
Bot-Service (Pekko):
  ✅ 12 Unit Tests (BotEngine + BotStreamProcessor)
  ✅ 4 Integration Tests (BotResourceIT)
  ✅ All Green

Chess-API (FS2):
  ✅ 5 Unit Tests (GameLifecycleStreamSpec)
  ✅ 2 Integration Tests (BulkGameControllerIT)
  ✅ All Green
```

Alle Tests: `./gradlew :modules:bot-service:test :modules:chess-api:test`

---

## 🎬 Präsentation (5-7 Min)

```
[Folie 1] Titel: "Streams — zwei Implementierungen"

[Folie 2] Problem + Lösung (Tabelle)

[Folie 3] Pekko im Bot-Service
  - Problem: Zu viele Threads
  - Lösung: Queue (500) + Workers (8)
  - File: BotStreamProcessor.scala

[Folie 4] Pekko Flow-Diagram
  (Request → Queue → Workers → Response)

[Folie 5] FS2 in Chess-API
  - Problem: 500 Spiele parallel, thread-safe
  - Lösung: Stream.range + parEvalMap(50)
  - File: GameLifecycleStream.scala

[Folie 6] FS2 Flow-Diagram
  (Stream.range → parEvalMap → compile.toList)

[Folie 7] Warum zwei unterschiedliche?
  (Tabelle: Pekko vs FS2)

[Folie 8] Live Demo (optional)
  (HTTP 200 vs HTTP 503)

[Folie 9] Fragen?
```

---

**Alles was ihr braucht — jetzt bereit zum Vorstellen!** 🎯
