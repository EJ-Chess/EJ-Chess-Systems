import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('error_rate');
const moveLatency = new Trend('move_latency_ms', true);

export const options = {
  scenarios: {
    game_lifecycle: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 },
        { duration: '30s', target: 10 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    // p95 latency across ALL requests must stay under 200 ms
    http_req_duration: ['p(95)<200'],
    // fewer than 1% of requests may fail
    error_rate: ['rate<0.01'],
    // move endpoint specifically: p95 < 300 ms (DB write included)
    move_latency_ms: ['p(95)<300'],
  },
};

const HEADERS = { 'Content-Type': 'application/json' };

export default function () {
  // 1. Create a new game
  const createRes = http.post(
    `${BASE_URL}/games`,
    JSON.stringify({}),
    { headers: HEADERS }
  );
  const createOk = check(createRes, {
    'create: status 201': (r) => r.status === 201,
    'create: has gameId': (r) => r.json('gameId') !== undefined,
  });
  errorRate.add(!createOk);
  if (!createOk) return;

  const gameId = createRes.json('gameId');

  // 2. Make opening move e2→e4
  const moveStart = Date.now();
  const moveRes = http.post(
    `${BASE_URL}/games/${gameId}/moves`,
    JSON.stringify({ from: 'e2', to: 'e4' }),
    { headers: HEADERS }
  );
  moveLatency.add(Date.now() - moveStart);
  const moveOk = check(moveRes, {
    'move e2e4: status 200': (r) => r.status === 200,
  });
  errorRate.add(!moveOk);

  // 3. Get current game state
  const stateRes = http.get(`${BASE_URL}/games/${gameId}`);
  const stateOk = check(stateRes, {
    'get state: status 200': (r) => r.status === 200,
    'get state: has fen': (r) => r.json('fen') !== undefined,
  });
  errorRate.add(!stateOk);

  // 4. Get legal moves
  const legalRes = http.get(`${BASE_URL}/games/${gameId}/moves`);
  const legalOk = check(legalRes, {
    'legal moves: status 200': (r) => r.status === 200,
    'legal moves: count > 0': (r) => r.json('count') > 0,
  });
  errorRate.add(!legalOk);

  // 5. Undo the move
  const undoRes = http.post(`${BASE_URL}/games/${gameId}/undo`, null, { headers: HEADERS });
  const undoOk = check(undoRes, {
    'undo: status 200': (r) => r.status === 200,
  });
  errorRate.add(!undoOk);

  // 6. Clean up
  http.del(`${BASE_URL}/games/${gameId}`);

  sleep(0.5);
}
