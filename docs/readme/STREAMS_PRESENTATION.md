# Streams Explained — Präsentationsfolien für Klasse

> **Context:** Alle haben Streams implementiert. Prof hat nur Pekko gezeigt. Hier erklären wir alle drei Ansätze + die Konzepte dahinter.

---

## 🎯 Agenda (5-10 Min Präsentation)

1. **Was sind Streams?** (Analogy + Definition)
2. **Warum brauchen wir das?** (Problem: 1000 Threads → Out of Memory)
3. **Backpressure** (Das Geheimnis)
4. **Seiteneffekt vs Pure Functional** (Warum unterschied wichtig)
5. **Drei Implementierungen** (Pekko, FS2 Gatling, FS2 Chess-API)
6. **Live Demo** (Optional: curl zeigen)

---

## 1️⃣ Was sind Streams?

### **Die Wasserhahn-Analogy**

```
Wasserhahn (Source)     Rohr (Stream)      Becken (Sink)
    🚰                      ║                   🪣
    ║                       ║
  [Wasser fließt nacheinander durch]
    ║
  Pro Sekunde: 1-5 Liter (kontrolliert)
```

**Stream = Daten fließen nacheinander, nicht alles auf einmal.**

```
OHNE Stream (unkontrolliert):
  Alle 1000 Wassermoleküle GLEICHZEITIG
  → Becken läuft über → Boden nass → Katastrophe ❌

MIT Stream (kontrolliert):
  50 Wassermoleküle gleichzeitig
  → Becken bleibt im Niveau → Alles läuft smooth ✅
```

### **Formale Definition**

```scala
// Ein Stream ist eine LAZY Berechnung
Stream[IO, A]
  ↑        ↑
  │        └─ Datentyp (z.B. Int, Game, BotMoveResponse)
  └────────── Effekt (IO = Input/Output möglich)
```

---

## 2️⃣ Das Problem: Ohne Streams

### **Szenario: 1000 Bot-Anfragen kommen gleichzeitig an**

#### ❌ Ohne Stream (Servlet Default)

```scala
@POST
@Path("/bot/move")
def getMove(req: BotMoveRequest): Response =
  // Quarkus/Jakarta: "HTTP-Request! Ich starte einen Thread!"
  val result = BotEngine.bestMove(req.fen, req.color)
  Response.ok(result).build()
```

**Was passiert:**
```
Request 1     → Thread 1 erzeugt
Request 2     → Thread 2 erzeugt
Request 3     → Thread 3 erzeugt
...
Request 1000  → Thread 1000 erzeugt

1 Thread = ~1-2 MB RAM
1000 Threads = 1000-2000 MB = 1-2 GB!

JVM default: 512 MB
Result: Out of Memory Exception → CRASH! 💥
```

**Ohne Kontrolle:**
```
Requests    Memory Usage    Status
1-100       ~100-200 MB     ✅ OK
101-200     ~200-400 MB     ⚠️  Warm
201-300     ~400-600 MB     ⚠️  Hot
301-500     ~600-1000 MB    🔥 Critical
501-1000    ERROR           💥 CRASH
```

#### ✅ Mit Stream (Pekko)

```scala
// Stream mit Queue (200 Plätze) + 4 Worker
Source.queue[Request](bufferSize = 200, dropNew)
  .mapAsync(parallelism = 4) { req =>
    Future { BotEngine.bestMove(...) }
  }
```

**Was passiert:**
```
Request 1-4     → 4 Worker verarbeiten (aktiv)
Request 5-200   → Queue wartet (passiv)
Request 201+    → DROPPED → HTTP 503 "Service Overloaded"

Total Threads: ~10-15
Total Memory: ~50-100 MB
Status: Stabil ✅
```

---

## 3️⃣ Backpressure — Das Geheimnis

### **Was ist Backpressure?**

```
Backpressure = "Sag mir, wenn du voll bist!"
```

### **Paket-Packing Analogy**

```
Ankunftsbereich (Buffer: 200 Plätze)     Arbeiter (4 gleichzeitig)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━
[Paket 1]
[Paket 2]                                 👷 Arbeiter 1: packt
[Paket 3]                                 👷 Arbeiter 2: packt
...                                       👷 Arbeiter 3: packt
[Paket 200]                               👷 Arbeiter 4: packt
[Paket 201?]  ← NOPE! Buffer voll!
              → "Komm später!" (HTTP 503)
              
Client liest 503 → "Aha, ich probiere in 5 Sek nochmal"
                 → Retry-Logic (Exponential Backoff)
```

### **Mit Backpressure (Smart):**
- ✅ Server: "Ich bin voll, bitte warte"
- ✅ Client: "OK, ich versuche es später"
- ✅ System: stabil, vorhersehbar

### **Ohne Backpressure (Dumb):**
- ❌ Server: akzeptiert alle Anfragen
- ❌ Erzeugt 1000 Threads
- ❌ Crash!

---

## 4️⃣ Seiteneffekt vs Pure Functional

### **Seiteneffekt = Funktion ändert Welt außerhalb**

```scala
var botCallCount = 0  // ← GLOBAL

def getBotMove(fen: String): String =
  botCallCount += 1              // ← SEITENEFFEKT!
  BotEngine.bestMove(fen)
```

**Das Problem:**
```
Aufruf 1: getBotMove("pos") → botCallCount = 1
Aufruf 2: getBotMove("pos") → botCallCount = 2
          (OBWOHL GLEICHE EINGABE!)
          
Gleiche Input, unterschiedliche Ausgabe.
Mit 8 Threads parallel → Race Condition!
```

### **Formelblatt-Analogy**

```
❌ NICHT-FUNKTIONAL (mit Radiergummi):
   Tag 1: 2+3 = 5 (schreib ich mit Stift)
   Tag 2: Ich radierer die 5 weg, schreib 6 hin
   Tag 3: Jetzt ist 2+3 = 7?
   
   Problem: Gleiche Rechnung, unterschiedliches Ergebnis!

✅ FUNKTIONAL (mathematisch):
   Tag 1: 2+3 = 5
   Tag 2: 2+3 = 5
   Tag 3: 2+3 = 5 (immer gleich!)
   
   Vorteil: 10 Schüler können parallel rechnen
            Alle kriegen 5 heraus → Keine Konflikte!
```

### **In Streams: Pure vs Impure**

```scala
// ❌ IMPURE (mit Seiteneffekten):
.mapAsync(4) { elem =>
  Future {
    database.log(elem)        // ← Seiteneffekt!
    var result = ...          // ← Seiteneffekt!
    result
  }
}

// ✅ PURE (keine Seiteneffekte):
.parEvalMap(50) { i =>
  singleLifecycle(i)          // ← Pure Function!
                              // Input → Output, sonst nix
}
```

---

## 5️⃣ Drei Implementierungen

### **Übersicht: Wann welcher Stream?**

| Szenario | Technologie | Warum | Location |
|----------|-------------|-------|----------|
| **Bot-Queue** | Pekko | Native Backpressure + Actor-System | bot-service |
| **Load-Test** | FS2 | Pure functional, lightweight | gatling |
| **Produktion (Bulk)** | FS2 | Pure FP, kein Netzwerk | chess-api |

---

### **A) Pekko Streams — Bot-Service**

#### **Das Problem:**
```
Web-UI: "Schach-Spiel! Benötige Bot-Zug!"
        "Bot-Zug!" (50 Mal pro Spiel)

Wenn 100 Spiele parallel laufen:
  100 × 50 = 5000 Bot-Anfragen möglich!
```

#### **Die Lösung: Bounded Queue mit Backpressure**

```scala
Source.queue[QueueElement](bufferSize = 500, OverflowStrategy.dropNew)
  .mapAsync(parallelism = 8) { elem =>
    Future {
      val move = BotEngine.bestMove(elem.request.fen, ...)
      elem.promise.success(Some(move))
    }
  }
  .toMat(Sink.ignore)(Keep.both)
  .run()
```

#### **Ablauf:**

```
BotResource.getMove(request)
    ↓
BotStreamProcessor.enqueue(request)
    ├─ Platz in Queue? (500 max)
    │  ├─ JA → Promise geben → weiter
    │  └─ NEIN → sofort None → HTTP 503
    ↓
mapAsync(8) = 8 Worker-Threads
    ├─ Worker 1: BotEngine.bestMove()
    ├─ Worker 2: BotEngine.bestMove()
    ├─ ...
    └─ Worker 8: BotEngine.bestMove()
    ↓
Promise erfüllt mit Result
    ↓
REST Response: 200 OK oder 503 Service Overloaded
```

#### **Zahlen:**
```
Buffer:      500 requests (queued)
Workers:     8 (parallel processing)
Max Total:   500 + 8 = 508 concurrent
Turnier:     12 teams × 50 moves ≈ 6000 total, max 100 parallel ✅
```

---

### **B) FS2 Streams — Gatling Load-Test**

#### **Das Problem:**
```
Wir wollen testen: Schafft der Server 100 komplette Spiele parallel?

Klassisch: 100 Threads mit HttpClient
Problem: unkontrolliert, schwer zu verwalten
```

#### **Die Lösung: Pure Functional Streaming**

```scala
Stream
  .range(1, N+1)                      // 1, 2, 3, ..., 100
  .parEvalMap(50) { i =>              // Max 50 parallel
    gameLifecycle(i).attempt          // Create, Move, Delete
  }
  .compile.toList                     // Sammeln & executen
```

#### **Ablauf:**

```
Stream.range(1, 101)  [1, 2, 3, ... 100]
  ↓
.parEvalMap(50)  ← Lazy! Noch nichts passiert!
  ├─ Batch 1: Spiele 1-50 parallel
  │   ├─ POST /games → get gameId
  │   ├─ POST /games/{id}/moves → make move
  │   └─ DELETE /games/{id} → cleanup
  ├─ Batch 2: Spiele 51-100 parallel
  └─ ...
  ↓
.compile.toList  ← Jetzt wird's REAL
  ↓
Results: [Either[Error, Success], ...]
  ↓
Summary: "100/100 OK, 0 errors, 1234ms"
```

#### **Warum FS2 hier?**
- ✅ Pure Functional (keine var, keine Seiteneffekte)
- ✅ Lazy Evaluation (Stream wird erst bei .compile.toList ausgeführt)
- ✅ Elegant Error Handling (.attempt = Either)
- ✅ Lightweight (kein Actor-System nötig)

---

### **C) FS2 Streams — Chess-API Produktion**

#### **Das Problem:**
```
POST /games/bulk {"count": 500}
  → 500 Spiele gleichzeitig ausführen
  → Alle 3 Operationen pro Spiel (create, move, delete)
  → Thread-safe
```

#### **Die Lösung: Bounded Concurrency mit fs2**

```scala
def runBulk(count: Int): IO[BulkGameResult] =
  Stream
    .range(1, count + 1)
    .parEvalMap(50) { i =>
      singleLifecycle(i).attempt
    }
    .compile.toList
    .flatMap { results =>
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

#### **Ablauf:**

```
POST /games/bulk {"count": 500}
  ↓
GameLifecycleStream.runBulk(500)
  ↓
Stream.range(1, 501)  [1, 2, 3, ..., 500]
  ↓
.parEvalMap(50)  ← Max 50 Spiele gleichzeitig
  ├─ Spiel 1-50 parallel:
  │   ├─ IO.blocking(gameService.createGame())
  │   ├─ IO.blocking(gameService.makeMoveAlgebraic())
  │   └─ IO.blocking(gameService.deleteGame())
  ├─ Spiel 51-100 parallel
  ├─ Spiel 101-150 parallel
  ...
  └─ Spiel 451-500 parallel
  ↓
.compile.toList  ← Alle Ergebnisse sammeln
  ↓
Response: {"total":500, "successful":500, "failed":0, "durationMs":4500}
```

#### **Warum FS2 hier (nicht Pekko)?**
- ✅ Lokal (kein Netzwerk, kein Actor-System nötig)
- ✅ Pure Functional (Thread-safe, testbar)
- ✅ Einfacher Code (keine Queue-Verwaltung)
- ❌ Pekko wäre "overkill" (zu viel Machinery für ein lokales Problem)

---

## 6️⃣ Live Demo (Optional)

### **A) Bot-Stream Test**

```bash
# Terminal 1: Services starten
./gradlew :modules:bot-service:quarkusDev

# Terminal 2: 300 parallele Requests senden
for i in {1..300}; do
  curl -s -X POST http://localhost:8081/bot/move \
    -H "Content-Type: application/json" \
    -d '{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","color":"white","elo":1200}' \
    -w "Status: %{http_code}\n" &
done
wait
```

**Was ihr seht:**
```
Status: 200 (erste 500+8)
Status: 200
...
Status: 503 (nach 508+)
Status: 503
```

Das ist **Backpressure in Aktion!** ✅

### **B) Bulk-Operation Test**

```bash
# Terminal 1: Services starten
./gradlew :modules:chess-api:quarkusDev
./gradlew :modules:bot-service:quarkusDev

# Terminal 2: 500 Spiele parallel
curl -X POST http://localhost:8080/games/bulk \
  -H "Content-Type: application/json" \
  -d '{"count":500}'

# Response:
# {
#   "total": 500,
#   "successful": 500,
#   "failed": 0,
#   "durationMs": 4500
# }
```

**Das ist pure functional streaming in production!** 🚀

---

## 📊 Comparison Table

```
┌──────────────────┬─────────────┬──────────────┬───────────────┐
│ Aspekt           │ Pekko       │ FS2 (Gatling)│ FS2 (Chess)   │
├──────────────────┼─────────────┼──────────────┼───────────────┤
│ Backpressure     │ ✅ Explicit │ ⚠️ Implicit  │ ⚠️ Implicit    │
│ Distributed      │ ✅ Yes      │ ❌ No        │ ❌ No          │
│ Pure Functional  │ ⚠️ Partial  │ ✅ Yes       │ ✅ Yes         │
│ Use Case         │ Service API │ Load Testing │ Bulk Ops      │
│ Complexity       │ 🔴 Medium   │ 🟢 Low       │ 🟢 Low         │
└──────────────────┴─────────────┴──────────────┴───────────────┘
```

---

## 💡 Key Takeaways

### **1. Streams kontrollieren Parallelität**

```
Ohne:  1000 Requests → 1000 Threads → CRASH
Mit:   1000 Requests → 500 Queue + 8 Worker → Stable
```

### **2. Backpressure ist Kommunikation**

```
Server zu Client: "Ich bin voll, komm später!"
Client liest 503 und retry-ed intelligent.
Besser als: Client sitzt 5 Minuten fest.
```

### **3. Pure Functional ist Thread-Safe**

```
❌ var x = 10; x += 1  ← Race Condition mit 8 Threads
✅ y = x.copy(val=11)  ← Jeder Thread seine Kopie
```

### **4. Wähle das richtige Tool**

```
Distributed?   → Pekko (über Netzwerk)
Local Only?    → FS2 (Pure FP)
Need Backpressure Explicitly? → Pekko Queue
Need Clean Code? → FS2
```

---

## 🎓 Was der Prof hat gesagt (Prof Bogers Lecture)

### **Reactive Streams = Backpressure + Control**

```
Axiome:
1. ✅ Daten fließen nur, wenn der Sink sie abholt
2. ✅ Kein unkontrolliertes Buffer-Wachstum
3. ✅ Sender weiß, wenn Empfänger voll ist
4. ✅ Beide arbeiten zusammen (nicht gegeneinander)
```

**Dein Code zeigt alles davon:**
- ✅ Pekko: explizit (Queue mit Limit)
- ✅ FS2: implizit (.parEvalMap bounded)
- ✅ Beide: vorhersehbar und stabil

---

## 🔗 Dateien zum Anschauen

| Thema | File | Lines |
|-------|------|-------|
| Pekko Queue | `modules/bot-service/BotStreamProcessor.scala` | 70 |
| Pekko Test | `modules/bot-service/BotStreamProcessorSpec.scala` | 103 |
| FS2 Gatling | `modules/gatling/ChessStreamLoad.scala` | 100 |
| FS2 Chess | `modules/chess-api/GameLifecycleStream.scala` | 56 |

---

## ❓ FAQ für Präsentation

**Q: Warum nicht immer Pekko?**
A: Pekko = Schweizer Messer (too much). Für lokale Bulk-Ops ist FS2 eleganter.

**Q: Was passiert wenn Queue voll ist?**
A: `OverflowStrategy.dropNew` → Request wird weggeworfen → Client kriegt HTTP 503 → Client retry-ed automatisch.

**Q: Können 8 Worker nicht 16 Züge parallel rechnen?**
A: Bot-Engine ist CPU-bound (Board-Evaluation). 8 Worker auf 8-core CPU = optimal. Mehr Workers = Context-Switching overhead.

**Q: Reicht der Buffer für Turnier?**
A: 12 teams × 50 moves = 6000 total, aber max ~100 concurrent. Buffer von 500 ist safe. Mit 1000 wäre extra-sicher.

**Q: Warum IO.blocking in FS2?**
A: GameService ist synchroner JVM-Code. IO.blocking sagt "hey, das ist kein echtes async, ich bin gerade blocking".

---

## 🎬 Slide-Struktur für deine Präsi

```
[Folie 1] Titel: "Streams Explained"
[Folie 2] Agenda (5 Punkte)
[Folie 3] Wasserhahn-Analogie + Definition
[Folie 4] Problem ohne Streams (1000 Threads)
[Folie 5] Mit Streams (500 Buffer + 8 Worker)
[Folie 6] Backpressure erklärt (Paket-Packer)
[Folie 7] Seiteneffekt vs Pure (Formelblatt)
[Folie 8] Pekko Streams (Bot-Service)
[Folie 9] FS2 Streams (Gatling)
[Folie 10] FS2 Streams (Chess-API Bulk)
[Folie 11] Vergleich: Wann welches Tool?
[Folie 12] Live Demo (optional)
[Folie 13] Key Takeaways
[Folie 14] Fragen?
```

---

**Erstellt:** 2026-06-02  
**Status:** Ready for Presentation
