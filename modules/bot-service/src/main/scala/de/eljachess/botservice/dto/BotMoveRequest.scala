package de.eljachess.botservice.dto

/** Request body for POST /bot/move */
case class BotMoveRequest(
  fen:   String, // FEN-encoded board position
  color: String, // "white" | "black" — the side to move
  elo:   Int     // bot strength (e.g. 800 / 1400 / 1800)
)
