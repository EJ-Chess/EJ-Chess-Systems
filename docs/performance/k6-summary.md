# k6 Load Test — Summary

**Script:** `performance/k6/chess-api.js`
**Tool:** [k6](https://k6.io) (install: `winget install k6` or `choco install k6`)

## How to run

```bash
# Start the chess-api first (in a separate terminal):
./gradlew :modules:chess-api:quarkusDev

# Run the load test:
k6 run performance/k6/chess-api.js

# Override base URL:
K6_BASE_URL=http://my-server:8080 k6 run performance/k6/chess-api.js
```

## Scenario

| Stage    | VUs | Duration |
|----------|-----|----------|
| Ramp-up  | 0→10 | 10 s   |
| Steady   | 10   | 30 s   |
| Ramp-down| 10→0 | 10 s   |

Each VU runs this lifecycle per iteration:
1. `POST /games` — create game
2. `POST /games/{id}/moves` — make move e2→e4
3. `GET /games/{id}` — read state
4. `GET /games/{id}/moves` — read legal moves
5. `POST /games/{id}/undo` — undo move
6. `DELETE /games/{id}` — cleanup

## Thresholds (pass/fail gate)

| Metric                    | Threshold     |
|---------------------------|---------------|
| `http_req_duration` p(95) | < 200 ms      |
| `error_rate`              | < 1%          |
| `move_latency_ms` p(95)   | < 300 ms      |

## Expected output (H2 dev mode, local machine)

```
scenarios: (100.00%) 1 scenario, 10 max VUs, 1m10s max duration
default: 0 looping VUs for 50s (gracefulStop: 30s)

✓ create: status 201
✓ create: has gameId
✓ move e2e4: status 200
✓ get state: status 200
✓ get state: has fen
✓ legal moves: status 200
✓ legal moves: count > 0
✓ undo: status 200

checks.........................: 100.00% ✓ ~400 ✗ 0
http_req_duration.............: avg=12ms  p(90)=25ms  p(95)=38ms
error_rate....................: 0.00%
move_latency_ms...............: p(95)=22ms

✓ http_req_duration p(95)<200ms threshold met
✓ error_rate<0.01 threshold met
✓ move_latency_ms p(95)<300ms threshold met
```

## Bottleneck & Fix

**Bottleneck identified:** The `POST /games/{id}/moves` endpoint was the slowest
operation due to `Fen.encode` being called twice per move (once for the response FEN,
once to persist the PGN via `GameManager.pgn()`).

**Fix applied:** `Fen.encodePlacement` was rewritten with a pre-allocated
`StringBuilder` instead of functional `map/foldLeft` string concatenation.
This reduced `encode` latency by 58% (see `docs/performance/jmh-results.md`),
translating to lower p95 on the move endpoint under load.
