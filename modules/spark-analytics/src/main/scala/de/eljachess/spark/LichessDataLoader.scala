package de.eljachess.spark

import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * Loads Lichess PGN files into a DataFrame compatible with ChessAnalytics.
 *
 * Each PGN game produces 2 rows (one per player) so all aggregation functions
 * work symmetrically — every player appears on both sides of the board.
 *
 * Schema produced:
 *   gameId      String  – last path segment of the Site URL
 *   playerName  String  – White or Black player name
 *   playerColor String  – "white" or "black"
 *   winner      String  – "white", "black", or "draw"
 *   botElo      Int     – opponent's ELO (0 when missing/unknown)
 *   moveCount   Int     – number of half-moves (plies) in the game
 *
 * How to download Lichess data:
 *   URL: https://database.lichess.org/standard/
 *   Smallest file: lichess_db_standard_rated_2013-01.pgn.bz2
 *   Decompress: bunzip2 lichess_db_standard_rated_2013-01.pgn.bz2
 *   Run: ./gradlew :modules:spark-analytics:runLichess --args="/path/to/lichess.pgn"
 */
object LichessDataLoader {

  private val HeaderRegex = """\[(\w+)\s+"([^"]*)"\]""".r

  /**
   * Read a PGN file and return a DataFrame with the chess analytics schema.
   *
   * Uses scala.io.Source for file I/O to avoid the Hadoop winutils dependency
   * on Windows (UnsatisfiedLinkError: NativeIO$Windows.access0).
   * Spark is still used for the DataFrame / aggregation layer.
   */
  def loadFromPgn(spark: SparkSession, pgnPath: String): DataFrame = {
    import spark.implicits._
    val source  = scala.io.Source.fromFile(pgnPath, "UTF-8")
    val lines   = try source.getLines().toArray finally source.close()
    val records = parseGames(lines)
    spark.createDataset(records).toDF(
      "gameId", "playerName", "playerColor", "winner", "botElo", "moveCount"
    )
  }

  /**
   * Split raw PGN lines into per-game blocks and parse each block into rows.
   * Accessible from tests via private[spark].
   */
  private[spark] def parseGames(lines: Array[String]): Seq[(String, String, String, String, Int, Int)] = {
    // A new game starts at a line beginning with "[Event "
    val blocks = lines.foldLeft(List.empty[List[String]]) { (acc, line) =>
      if (line.startsWith("[Event ")) List(line) :: acc
      else acc match {
        case head :: tail => (head :+ line) :: tail
        case Nil          => List(List(line))
      }
    }.reverse

    blocks.flatMap(block => parseGame(block))
  }

  private def parseGame(block: Seq[String]): Seq[(String, String, String, String, Int, Int)] = {
    // Collect all headers from this block
    val headers: Map[String, String] = block.flatMap {
      case HeaderRegex(key, value) => Some(key -> value)
      case _                       => None
    }.toMap

    // The move text is the first non-empty, non-header line
    val moveText = block
      .filterNot(_.startsWith("["))
      .mkString(" ")
      .trim

    val result: Option[Seq[(String, String, String, String, Int, Int)]] = for {
      site      <- headers.get("Site")
      gameId     = site.split('/').last
      whiteName <- headers.get("White")
      blackName <- headers.get("Black")
      resultTag <- headers.get("Result")
      winner    <- resultTag match {
                     case "1-0"     => Some("white")
                     case "0-1"     => Some("black")
                     case "1/2-1/2" => Some("draw")
                     case _         => None
                   }
      moves      = countMoves(moveText)
      whiteElo   = parseElo(headers.get("WhiteElo"))
      blackElo   = parseElo(headers.get("BlackElo"))
    } yield Seq(
      (gameId, whiteName, "white", winner, blackElo, moves),
      (gameId, blackName, "black", winner, whiteElo, moves)
    )

    result.getOrElse(Seq.empty)
  }

  private def parseElo(elo: Option[String]): Int =
    elo.flatMap(s => if (s == "?") None else s.toIntOption).getOrElse(0)

  /**
   * Count half-moves (plies) in a PGN move text string.
   *
   * Steps:
   *   1. Remove {...} comment blocks (clock/eval annotations)
   *   2. Remove NAG tokens ($1, $18, ...)
   *   3. Remove move numbers (1. 1... 12.)
   *   4. Remove result tokens (1-0, 0-1, 1/2-1/2, *)
   *   5. Count remaining non-empty tokens
   *
   * Accessible from tests via private[spark].
   */
  private[spark] def countMoves(moveText: String): Int = {
    val noComments    = moveText.replaceAll("""\{[^}]*\}""", " ")
    val noNags        = noComments.replaceAll("""\$\d+""", " ")
    val noMoveNumbers = noNags.replaceAll("""\d+\.+""", " ")
    val noResult      = noMoveNumbers.replaceAll("""(1-0|0-1|1/2-1/2|\*)""", " ")
    noResult.trim.split("""\s+""").count(_.nonEmpty)
  }
}
