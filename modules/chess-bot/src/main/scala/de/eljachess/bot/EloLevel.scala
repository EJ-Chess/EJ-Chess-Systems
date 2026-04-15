package de.eljachess.bot

case class EloLevel(elo: Int, name: String)

object EloLevel:
  val Beginner     = EloLevel(800,  "Beginner")
  val Intermediate = EloLevel(1400, "Intermediate")
  val Advanced     = EloLevel(1800, "Advanced")

  val predefined: List[EloLevel] = List(Beginner, Intermediate, Advanced)

  def custom(elo: Int): EloLevel = EloLevel(elo, s"Custom ($elo)")
