package de.eljachess.analytics

case class PlayerVictory(player: String, victories: Long)
case class ColorWin(color: String, totalWins: Long)
case class PlayerElo(player: String, avgEloBeat: Double)

case class AnalyticsResult(
  status: String,
  runAt: Option[String],
  victoriesPerPlayer: List[PlayerVictory],
  winsPerColor: List[ColorWin],
  avgEloBeatPerPlayer: List[PlayerElo],
  bestPlayer: String,
  dataSource: String = "local"
)

object AnalyticsResult {
  val idle: AnalyticsResult = AnalyticsResult("IDLE", None, Nil, Nil, Nil, "", "")
}
