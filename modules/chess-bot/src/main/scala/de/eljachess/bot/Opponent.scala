package de.eljachess.bot

sealed trait Opponent

case object HumanOpponent extends Opponent

case class BotOpponent(elo: EloLevel) extends Opponent
