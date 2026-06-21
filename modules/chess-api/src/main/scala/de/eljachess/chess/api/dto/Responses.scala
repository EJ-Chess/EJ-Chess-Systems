package de.eljachess.chess.api.dto

import java.time.Instant

// Game Management Responses
case class GameCreatedResponse(
  gameId: String,
  fen: String
)

case class GameStateResponse(
  gameId: String,
  fen: String,
  currentTurn: String,        // "WHITE" or "BLACK"
  fullmoveNumber: Int,
  halfmoveClock: Int,
  inCheck: Boolean,
  inCheckmate: Boolean,
  inStalemate: Boolean,
  legalMovesCount: Int
)

// Move Responses
case class MoveSuccessResponse(
  success: Boolean = true
)

case class MoveErrorResponse(
  success: Boolean = false,
  error: String,
  legalMoves: Option[List[String]] = None,
  gameId: Option[String] = None
)

case class LegalMovesResponse(
  gameId: String,
  currentTurn: String,
  legalMoves: List[MoveNotation],
  count: Int
)

case class MoveNotation(
  from: String,
  to: String,
  promotion: Option[String] = None
)

// Export Responses
case class FenResponse(
  gameId: String,
  fen: String
)

case class PgnResponse(
  gameId: String,
  pgn: String
)

// Undo/Redo Responses
case class UndoRedoResponse(
  success: Boolean,
  fen: String
)

case class UndoRedoErrorResponse(
  success: Boolean = false,
  error: String
)

// Error Response (consistent across all endpoints)
case class ErrorResponse(
  error: String,
  details: Option[String] = None,
  gameId: Option[String] = None,
  timestamp: String = Instant.now().toString()
)

// Import Response
case class ImportResponse(
  success: Boolean,
  fen: Option[String] = None,
  moveCount: Option[Int] = None,
  error: Option[String] = None
)

// Bulk Game Operations
case class BulkGameResult(
  total: Int,
  successful: Int,
  failed: Int,
  durationMs: Long
)

// Analytics Export — one row per completed game
case class AnalyticsGameRow(
  gameId:      String,
  playerName:  String,
  playerColor: String, // "white" or "black"
  winner:      String, // "white", "black", or "draw"
  botElo:      Int,    // 0 for human vs human
  moveCount:   Int
)
