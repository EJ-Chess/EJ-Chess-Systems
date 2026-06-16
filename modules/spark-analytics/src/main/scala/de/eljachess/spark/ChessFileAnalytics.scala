package de.eljachess.spark

/**
 * Step 1 – File-based analytics.
 *
 * Reads chess game results from a CSV file and prints aggregated statistics:
 *   - Victories per player (highscore table)
 *   - Wins per color (white / black)
 *   - Average bot ELO beaten per player
 *   - Best player (most victories)
 *
 * Usage:
 *   ./gradlew :modules:spark-analytics:run            (uses bundled chess_games.csv)
 *   ./gradlew :modules:spark-analytics:run --args="/path/to/games.csv"
 */
object ChessFileAnalytics {

  def main(args: Array[String]): Unit = {
    val spark = ChessAnalytics.createLocalSpark("Chess File Analytics")

    // Use toURI → Paths.get to correctly decode spaces/special chars in the path
    val csvPath =
      if (args.length > 0) args(0)
      else java.nio.file.Paths
        .get(getClass.getClassLoader.getResource("chess_games.csv").toURI)
        .toString

    val df = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(csvPath)
      .cache()

    println()
    println("══════════════════════════════════════")
    println("  Chess Game Analytics  (File Mode)")
    println("══════════════════════════════════════")
    println()

    println("── Victories per Player ─────────────")
    ChessAnalytics.victoriesPerPlayer(df).show(truncate = false)

    println("── Wins per Color ───────────────────")
    ChessAnalytics.winsPerColor(df).show(truncate = false)

    println("── Average Bot ELO Beaten per Player ─")
    ChessAnalytics.avgBotEloBeatByPlayer(df).show(truncate = false)

    val best = ChessAnalytics.bestPlayer(df)
    println(s"── Highscore / Best Player: $best\n")

    spark.stop()
  }
}
