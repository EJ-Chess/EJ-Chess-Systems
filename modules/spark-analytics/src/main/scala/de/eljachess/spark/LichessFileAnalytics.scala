package de.eljachess.spark

/**
 * Step 3 – Lichess PGN file analytics.
 *
 * Reads chess game results from a Lichess PGN file and prints aggregated
 * statistics:
 *   - Victories per player (top 20)
 *   - Wins per color (white / black)
 *   - Average bot ELO beaten per player (top 20)
 *   - Best player (most victories)
 *
 * How to download Lichess data:
 *   URL: https://database.lichess.org/standard/
 *   Smallest file: lichess_db_standard_rated_2013-01.pgn.bz2
 *   Decompress: bunzip2 lichess_db_standard_rated_2013-01.pgn.bz2
 *   Run: ./gradlew :modules:spark-analytics:runLichess --args="/path/to/lichess.pgn"
 */
object LichessFileAnalytics {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: ./gradlew :modules:spark-analytics:runLichess --args=\"/path/to/lichess.pgn\"")
      sys.exit(1)
    }

    val spark = ChessAnalytics.createLocalSpark("Lichess File Analytics")
    val df    = LichessDataLoader.loadFromPgn(spark, args(0)).cache()

    println()
    println("══════════════════════════════════════")
    println("  Chess Game Analytics  (Lichess PGN)")
    println("══════════════════════════════════════")
    println()

    println(s"── Total game rows: ${df.count()} (2 per game) ──")
    println()

    println("── Victories per Player (top 20) ────")
    ChessAnalytics.victoriesPerPlayer(df).show(20, truncate = false)

    println("── Wins per Color ───────────────────")
    ChessAnalytics.winsPerColor(df).show(truncate = false)

    println("── Average Bot ELO Beaten per Player (top 20) ─")
    ChessAnalytics.avgBotEloBeatByPlayer(df).show(20, truncate = false)

    val best = ChessAnalytics.bestPlayer(df)
    println(s"── Highscore / Best Player: $best\n")

    spark.stop()
  }
}
