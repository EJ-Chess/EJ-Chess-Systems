package de.eljachess.botservice.dto

/** Response body for POST /bot/move */
case class BotMoveResponse(
  from: String, // e.g. "e2"
  to:   String  // e.g. "e4"
)
