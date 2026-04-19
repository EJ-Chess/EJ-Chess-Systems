package de.eljachess.bot

import de.eljachess.chess.model.Color

case class GameSetup(
  playerName:  String,
  playerColor: Color,
  opponent:    Opponent
)
