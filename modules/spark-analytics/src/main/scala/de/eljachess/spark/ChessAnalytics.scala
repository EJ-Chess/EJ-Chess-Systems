package de.eljachess.spark

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

/**
 * Shared Spark aggregation logic for chess game analytics.
 *
 * All functions accept a DataFrame with schema:
 *   gameId      String  – unique game identifier
 *   playerName  String  – human player's name
 *   playerColor String  – "white" or "black" (the color the player was assigned)
 *   winner      String  – "white", "black", or "draw"
 *   botElo      Int     – ELO rating of the opponent bot
 *   moveCount   Int     – total number of moves in the game
 */
object ChessAnalytics {

  /** Create a local SparkSession suitable for file-based analytics. */
  def createLocalSpark(appName: String): SparkSession =
    SparkSession.builder()
      .appName(appName)
      .config("spark.master", "local[*]")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

  /**
   * Count victories per player.
   * A player wins when their assigned color matches the winner column.
   * Rows where winner = "draw" are excluded.
   */
  def victoriesPerPlayer(df: DataFrame): DataFrame =
    df.filter(col("playerColor") === col("winner"))
      .groupBy("playerName")
      .agg(count("*").as("victories"))
      .orderBy(col("victories").desc)

  /**
   * Count total wins by winning color (white / black).
   * Draws are excluded.
   */
  def winsPerColor(df: DataFrame): DataFrame =
    df.filter(col("winner") =!= lit("draw"))
      .groupBy("winner")
      .agg(count("*").as("total_wins"))
      .orderBy(col("total_wins").desc)

  /**
   * Average bot ELO beaten per player, across their won games only.
   * Higher value = player regularly beats stronger bots.
   */
  def avgBotEloBeatByPlayer(df: DataFrame): DataFrame =
    df.filter(col("playerColor") === col("winner"))
      .groupBy("playerName")
      .agg(avg(col("botElo")).as("avg_bot_elo_beaten"))
      .orderBy(col("avg_bot_elo_beaten").desc)

  /**
   * Return the name of the player with the most victories.
   * Returns "No games played" when the dataset is empty or all results are draws.
   */
  def bestPlayer(df: DataFrame): String =
    victoriesPerPlayer(df).limit(1).collect() match {
      case Array(row) => row.getString(0)
      case _          => "No games played"
    }
}
