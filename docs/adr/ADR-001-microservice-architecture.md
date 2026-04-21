# ADR-001 — Microservice Architecture

**Date:** 2026-04-19  
**Status:** Accepted  
**Branch:** feature/microservice-approach-a

---

## Context

The original architecture was a **modular monolith**:

```
core/                 ← pure chess engine (Board, GameController, Fen, …)
modules/chess-api/    ← Quarkus REST service + bot logic (tightly coupled)
modules/chess-bot/    ← GreedyRandomBot + JavaFX desktop GUI (same JVM process)
modules/chess-ui/     ← React/TypeScript web frontend
```

`chess-api` had a direct Gradle dependency on `chess-bot` and instantiated
`GreedyRandomBot` inline within `GameService`. This made bot logic inseparable
from the game-session service — they shared the same process, memory, and
deployment lifecycle.

The task was to split the system into independent, deployable microservices.

---

## Decision

Introduce a **dedicated `bot-service`** microservice and remove the direct
bot dependency from `chess-api` (renamed conceptually to `game-service`).

### New service topology

```
chess-ui  (React SPA, served via Vite / nginx)
    │  HTTP REST
    ▼
game-service  (modules/chess-api, port 8080)
    │  HTTP REST  POST /bot/move
    ▼
bot-service   (modules/bot-service, port 8081)
    │  depends on
    ▼
core          (shared chess engine — NOT a service, compile-time only)
```

### `bot-service` (new — `modules/bot-service`)

- Quarkus REST service on **port 8081**
- Single stateless endpoint: `POST /bot/move`
  - Request:  `{ "fen": "…", "color": "white"|"black", "elo": 800–2000 }`
  - Response: `{ "from": "e2", "to": "e4" }`
- Contains `BotEngine` — the greedy-random algorithm (adapted from
  `GreedyRandomBot` in `chess-bot`, depends only on `:core`)
- **Stateless**: no session state, no database — pure compute

### `game-service` (modified — `modules/chess-api`)

- Still manages game sessions in memory (`GameService`)
- Bot configuration (ELO, bot color) is stored per game in a `TrieMap[String, BotConfig]`
- `GameController` is always created **without** a `Bot` — bot turns are now
  orchestrated externally by `GameService`
- After each player move, `GameService.applyBotMoveIfNeeded` calls `bot-service`
  via `java.net.http.HttpClient` and applies the returned move
- If `bot-service` is unreachable the move is silently skipped (game remains playable)
- **Removed dependencies:** `chess-bot` Gradle module, ScalaFX, JavaFX
- **Removed:** `LocalUIStartup.scala` — desktop GUI startup does not belong in a
  stateless REST service

### `chess-ui` (unchanged)

Already a standalone SPA; calls only `game-service`. No changes required.

### `chess-bot` (unchanged, kept for desktop client)

The `chess-bot` module is retained for the JavaFX desktop client (`BotMain`).
It is no longer a dependency of `game-service`.

---

## Consequences

### Positive
- **Independent deployability**: `bot-service` can be scaled, replaced, or
  updated without touching `game-service`.
- **Separation of concerns**: game session management vs. AI computation are
  cleanly separated.
- **Testability**: `BotEngine` is a pure Scala object with no I/O — trivially
  unit-testable without a running server.
- **Technology freedom**: `bot-service` could be rewritten in a different
  language/framework without affecting `game-service`.

### Negative / Trade-offs
- **Network latency**: each bot move now incurs an HTTP round-trip
  (negligible for a chess UI but worth noting).
- **Operational complexity**: two services must be running for bot games to
  work (instead of one). Bot unavailability degrades silently.
- **In-memory sessions**: `game-service` still holds all game state in memory.
  This is a known limitation to be addressed by a future persistence service
  (e.g. PostgreSQL + Hibernate Reactive).

---

## Alternatives Considered

| Option | Reason rejected |
|--------|----------------|
| Keep bot in chess-api | Does not achieve microservice separation |
| gRPC instead of REST | Adds complexity; HTTP/JSON is sufficient for low-frequency chess moves |
| Message queue (Kafka) | Overkill for synchronous request/response semantics of a chess move |
| Move bot logic into core | Core is a compile-time library, not a service |
