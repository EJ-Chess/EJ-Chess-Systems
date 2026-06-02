# Streams — Was wir gebaut haben

Wir haben zwei verschiedene Stream-Implementierungen: **Pekko** im Bot-Service und **FS2** in der Chess-API. Prof hat nur Pekko gezeigt, wir haben aber gemerkt dass das nicht überall passt.

---

## Pekko — Bot-Service

### Das Problem

```
Spiele laufen, Web-UI feuert Bot-Anfragen ab.
Ein Spiel = 50-60 Züge = 50-60 Bot-Anfragen.
Wenn zu viele gleichzeitig kommen → zu viele Threads → Crash.
```

### Was wir gebaut haben

```scala
// BotStreamProcessor.scala
Source.queue[QueueElement](bufferSize = 500, OverflowStrategy.dropNew)
  .mapAsync(parallelism = 8) { elem =>
    Future {
      val move = BotEngine.bestMove(elem.request.fen, ...)
      elem.promise.success(Some(move))
    }
  }
```

Einfach: Queue mit 500 Plätzen, 8 Worker die parallel rechnen. Wenn Queue voll → HTTP 503.

**Files:**
- `modules/bot-service/src/main/scala/de/eljachess/botservice/BotStreamProcessor.scala` (70 Lines)
- `modules/bot-service/src/test/scala/de/eljachess/botservice/BotStreamProcessorSpec.scala` (103 Lines, 7 Tests)
- `modules/bot-service/src/test/scala/de/eljachess/botservice/BotResourceIT.scala` (64 Lines, 4 Tests)

---

## FS2 — Chess-API Bulk Operations

### Das Problem

```
POST /games/bulk {"count": 500}

500 Spiele gleichzeitig spielen (jedes: create → move → delete).
Thread-safe. Ohne zu viel Code-Chaos.
```

### Was wir gebaut haben

```scala
// GameLifecycleStream.scala
def runBulk(count: Int): IO[BulkGameResult] =
  Stream
    .range(1, count + 1)
    .parEvalMap(50) { i =>
      singleLifecycle(i).attempt
    }
    .compile.toList
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

Stream mit 50 parallelen Spielen. Lazy evaluiert, dann ausgeführt, Results gesammelt.

**Files:**
- `modules/chess-api/src/main/scala/de/eljachess/chess/api/service/GameLifecycleStream.scala` (56 Lines)
- `modules/chess-api/src/main/scala/de/eljachess/chess/api/controller/BulkGameController.scala` (20 Lines)
- `modules/chess-api/src/test/scala/de/eljachess/chess/api/service/GameLifecycleStreamSpec.scala` (36 Lines, 5 Tests)
- `modules/chess-api/src/test/scala/de/eljachess/chess/api/controller/BulkGameControllerIT.scala` (40 Lines, 2 Tests)

---

## Warum zwei unterschiedliche?

**Pekko:** Queue-basiert, Backpressure explicit. Passt für externe HTTP-Anfragen (Bot-Service ist öffentlich).

**FS2:** Pure Functional, lazy, elegant. Passt für interne Operationen (Bulk ist lokal auf der Chess-API).

| Aspekt | Pekko | FS2 |
|--------|-------|-----|
| Verteilt | Ja | Nein |
| Backpressure | Explicit (Queue) | Implicit (parEvalMap) |
| Code-Stil | Imperative (Promise, Future) | Pure FP (Stream, IO) |

FS2 ist sauberer für diese Stelle weil alles lokal ist und keine externe Backpressure nötig.

---

## Zusammengefasst

- **Bot-Service:** Pekko mit Queue + 8 Worker. Requests kommen, Queue verwaltet, Worker rechnen.
- **Chess-API:** FS2 Streams mit parEvalMap(50). 500 Spiele, aber max 50 gleichzeitig.

Pekko brauchten wir, FS2 haben wir extra gemacht weil's eleganter ist für den Use-Case. 🚀
