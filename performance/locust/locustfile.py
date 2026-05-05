"""
Chess API – Locust load test
Run:  locust -f locustfile.py --host=http://localhost:8080
UI:   http://localhost:8089

Thresholds (same as k6 / Gatling):
  p95 response time  < 200 ms
  error rate         < 1 %
"""

from locust import HttpUser, task, between, events
import json
import logging

log = logging.getLogger(__name__)


class ChessPlayer(HttpUser):
    """Simulates one concurrent player interacting with the chess API."""

    wait_time = between(0.3, 1.0)   # think-time between requests

    # ── lifecycle ──────────────────────────────────────────────────────────────

    def on_start(self):
        """Create a fresh game when the virtual user spawns."""
        self.game_id = None
        self.move_count = 0

        with self.client.post(
            "/games",
            json={},
            headers={"Content-Type": "application/json"},
            name="POST /games  [setup]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 201:
                self.game_id = resp.json()["gameId"]
            else:
                resp.failure(f"create game failed: {resp.status_code}")

    def on_stop(self):
        """Delete the game when the virtual user finishes."""
        if self.game_id:
            self.client.delete(
                f"/games/{self.game_id}",
                name="DELETE /games/[id]  [teardown]",
            )

    # ── tasks (weight = relative frequency) ───────────────────────────────────

    @task(4)
    def read_game_state(self):
        """Most common operation: read current FEN + metadata."""
        if not self.game_id:
            return
        with self.client.get(
            f"/games/{self.game_id}",
            name="GET /games/[id]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                data = resp.json()
                if "fen" not in data:
                    resp.failure("missing 'fen' in response")
            else:
                resp.failure(f"status {resp.status_code}")

    @task(3)
    def read_legal_moves(self):
        """Read legal moves — triggers Board.legalMoves (hot path)."""
        if not self.game_id:
            return
        with self.client.get(
            f"/games/{self.game_id}/moves",
            name="GET /games/[id]/moves",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                data = resp.json()
                if data.get("count", 0) == 0:
                    resp.failure("no legal moves returned")
            else:
                resp.failure(f"status {resp.status_code}")

    @task(2)
    def make_move_then_undo(self):
        """
        Make the opening move e2→e4, then immediately undo it.
        This keeps the game at the start position so the task is repeatable
        and exercises both the write path (DB update) and undo path.
        """
        if not self.game_id:
            return

        with self.client.post(
            f"/games/{self.game_id}/moves",
            json={"from": "e2", "to": "e4"},
            headers={"Content-Type": "application/json"},
            name="POST /games/[id]/moves",
            catch_response=True,
        ) as resp:
            if resp.status_code not in (200, 400):
                resp.failure(f"unexpected status {resp.status_code}")
            elif resp.status_code == 400:
                # Move was already played — undo first on next iteration
                return

        # Undo to reset to start position
        self.client.post(
            f"/games/{self.game_id}/undo",
            json={},
            headers={"Content-Type": "application/json"},
            name="POST /games/[id]/undo",
        )

    @task(1)
    def create_and_delete_game(self):
        """Create a throwaway game and delete it — tests full create/delete cycle."""
        with self.client.post(
            "/games",
            json={},
            headers={"Content-Type": "application/json"},
            name="POST /games  [ephemeral]",
            catch_response=True,
        ) as resp:
            if resp.status_code != 201:
                resp.failure(f"create failed: {resp.status_code}")
                return
            temp_id = resp.json()["gameId"]

        self.client.delete(
            f"/games/{temp_id}",
            name="DELETE /games/[id]  [ephemeral]",
        )
