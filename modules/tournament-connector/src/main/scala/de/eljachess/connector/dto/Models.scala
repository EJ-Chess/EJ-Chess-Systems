package de.eljachess.connector.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// ── Auth ──────────────────────────────────────────────────────────────────────

case class RegisterRequest(name: String, isBot: Boolean)

@JsonIgnoreProperties(ignoreUnknown = true)
case class RegisterResponse(id: String, token: String)

// ── Tournament list ───────────────────────────────────────────────────────────

/** Minimal view of a tournament returned by GET /api/tournament.
 *  The external server may return additional fields — all are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class TournamentSummary(
  id: String,
  status: String,
  fullName: Option[String] = None
)

// ── Tournament stream events (NDJSON from GET /api/tournament/{id}/stream) ───

/** Payload inside a "gameStart" event. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class GameStartInfo(
  round: Int,
  gameId: String,
  color: String   // "white" | "black" — the color assigned to THIS bot
)

/** Payload inside a "roundStarted" / "roundFinished" event. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class RoundInfo(round: Int)

/** Payload inside a "tournamentFinished" event. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class WinnerInfo(
  id: Option[String] = None,
  name: Option[String] = None
)

/** One line of the tournament NDJSON stream.
 *  Exactly one field will be non-None per line. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class TournamentStreamEvent(
  gameStart: Option[GameStartInfo] = None,
  tournamentStarted: Option[Boolean] = None,
  roundStarted: Option[RoundInfo] = None,
  roundFinished: Option[RoundInfo] = None,
  tournamentFinished: Option[WinnerInfo] = None
)

// ── Game stream events (NDJSON from GET /api/tournament/{id}/game/{gId}/stream)

@JsonIgnoreProperties(ignoreUnknown = true)
case class ClockInfo(limit: Int, increment: Int)

/** Full snapshot sent as the first event on every game stream. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class GameStateInfo(
  fen: String,
  turn: String,           // "white" | "black"
  status: String,         // "ongoing" | "checkmate" | "stalemate" | "draw" | …
  winner: Option[String] = None,
  clock: Option[ClockInfo] = None,
  moves: Option[List[String]] = None
)

/** Incremental event: a move was just played. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class MoveInfo(
  uci: String,
  fen: String,
  turn: String,           // whose turn it is AFTER this move
  clock: Option[ClockInfo] = None
)

/** Terminal event: the game is over. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class GameEndInfo(
  winner: Option[String] = None,  // "white" | "black" | null (draw)
  status: String
)

/** One line of the game NDJSON stream. */
@JsonIgnoreProperties(ignoreUnknown = true)
case class GameStreamEvent(
  gameState: Option[GameStateInfo] = None,
  move: Option[MoveInfo] = None,
  gameEnd: Option[GameEndInfo] = None
)

// ── Bot-service DTOs ──────────────────────────────────────────────────────────

case class BotMoveRequest(fen: String, color: String, elo: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
case class BotMoveResponse(from: String, to: String):
  /** Converts to UCI notation (e.g. "e2" + "e4" → "e2e4"). */
  def toUci: String = from + to
