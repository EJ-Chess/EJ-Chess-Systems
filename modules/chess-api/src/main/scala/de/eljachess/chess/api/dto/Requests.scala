package de.eljachess.chess.api.dto

// Game Management
case class CreateGameRequest(
  playerName:  Option[String] = None,   // defaults to "Player"
  playerColor: Option[String] = None,   // "white" | "black", defaults to "white"
  opponent:    Option[String] = None,   // "human" | "bot", defaults to "human"
  botElo:      Option[Int]    = None    // ELO if opponent=bot, defaults to 1400
)

case class ImportRequest(
  pgn: Option[String] = None,
  fen: Option[String] = None
)

// Moves
case class MakeMoveRequest(
  from: Option[String] = None,      // e.g., "e2"
  to: Option[String] = None,        // e.g., "e4"
  promotion: Option[String] = None, // e.g., "Q", "R", "B", "N"
  san: Option[String] = None        // e.g., "e4", "Nf3"
)

case class UndoRequest()

case class RedoRequest()

// Bulk operations
case class BulkGameRequest(
  count: Int
)
