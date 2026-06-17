package de.eljachess.spark

import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

import java.io.{File, PrintWriter}

/**
 * Unit / integration tests for LichessDataLoader.
 *
 * A local[*] SparkSession is created once for the whole suite and torn down
 * in afterAll.  The PGN parsing tests use no Spark at all — just pure Scala.
 */
@RunWith(classOf[JUnitRunner])
class LichessDataLoaderSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  @volatile private var spark: SparkSession = _

  override def beforeAll(): Unit =
    spark = SparkSession.builder()
      .master("local[*]")
      .appName("LichessDataLoaderSpec")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "1")
      .getOrCreate()

  override def afterAll(): Unit =
    if (spark != null) spark.stop()

  // ── Sample PGN ──────────────────────────────────────────────────────────────

  private val samplePgn: String =
    """[Event "Rated Blitz game"]
      |[Site "https://lichess.org/AbCdEfGh"]
      |[White "Magnus"]
      |[Black "Hikaru"]
      |[Result "1-0"]
      |[WhiteElo "2800"]
      |[BlackElo "2750"]
      |[TimeControl "180+2"]
      |[Termination "Normal"]
      |
      |1. e4 { [%clk 0:03:00] } 1... e5 { [%clk 0:03:00] } 2. Nf3 { [%clk 0:02:58] } 2... Nc6 { [%clk 0:02:59] } 3. Bb5 { [%clk 0:02:56] } 3... a6 { [%clk 0:02:57] } 1-0
      |
      |[Event "Rated Bullet game"]
      |[Site "https://lichess.org/XyZ12345"]
      |[White "DrNykterstein"]
      |[Black "Magnus"]
      |[Result "0-1"]
      |[WhiteElo "2700"]
      |[BlackElo "2800"]
      |[TimeControl "60+0"]
      |[Termination "Time forfeit"]
      |
      |1. d4 { [%clk 0:01:00] } 1... d5 { [%clk 0:01:00] } 2. c4 { [%clk 0:00:59] } 2... c6 { [%clk 0:00:59] } 0-1""".stripMargin

  /** Write samplePgn to a temp file and return its absolute path. */
  private def writeTempPgn(): String = {
    val f = File.createTempFile("lichess-test", ".pgn")
    f.deleteOnExit()
    val pw = new PrintWriter(f)
    try pw.print(samplePgn) finally pw.close()
    f.getAbsolutePath
  }

  private def pgnLines: Array[String] = samplePgn.split("\n")

  // ── parseGames ──────────────────────────────────────────────────────────────

  "parseGames" should "produce 4 rows for a 2-game PGN" in {
    LichessDataLoader.parseGames(pgnLines) should have size 4
  }

  it should "extract the correct gameId from the Site URL" in {
    val rows = LichessDataLoader.parseGames(pgnLines)
    val ids  = rows.map(_._1).distinct
    ids should contain ("AbCdEfGh")
    ids should contain ("XyZ12345")
  }

  it should "produce correct tuples for all 4 rows" in {
    val rows = LichessDataLoader.parseGames(pgnLines)
    val map  = rows.groupBy(_._1)   // group by gameId

    val game1 = map("AbCdEfGh")
    val whiteRow1 = game1.find(_._3 == "white").get
    val blackRow1 = game1.find(_._3 == "black").get

    // gameId, playerName, playerColor, winner, botElo, moveCount
    whiteRow1._2 should be ("Magnus")
    whiteRow1._4 should be ("white")
    whiteRow1._5 should be (2750)
    whiteRow1._6 should be (6)

    blackRow1._2 should be ("Hikaru")
    blackRow1._4 should be ("white")
    blackRow1._5 should be (2800)
    blackRow1._6 should be (6)

    val game2 = map("XyZ12345")
    val whiteRow2 = game2.find(_._3 == "white").get
    val blackRow2 = game2.find(_._3 == "black").get

    whiteRow2._2 should be ("DrNykterstein")
    whiteRow2._4 should be ("black")
    whiteRow2._5 should be (2800)
    whiteRow2._6 should be (4)

    blackRow2._2 should be ("Magnus")
    blackRow2._4 should be ("black")
    blackRow2._5 should be (2700)
    blackRow2._6 should be (4)
  }

  it should "map Result '1-0' to winner='white'" in {
    val rows = LichessDataLoader.parseGames(pgnLines)
    val game1Rows = rows.filter(_._1 == "AbCdEfGh")
    game1Rows.forall(_._4 == "white") should be (true)
  }

  it should "map Result '0-1' to winner='black'" in {
    val rows = LichessDataLoader.parseGames(pgnLines)
    val game2Rows = rows.filter(_._1 == "XyZ12345")
    game2Rows.forall(_._4 == "black") should be (true)
  }

  it should "skip games with Result '*'" in {
    val unknownPgn =
      """[Event "Rated Blitz game"]
        |[Site "https://lichess.org/SkipGame1"]
        |[White "Alpha"]
        |[Black "Beta"]
        |[Result "*"]
        |[WhiteElo "1500"]
        |[BlackElo "1500"]
        |
        |1. e4 *""".stripMargin
    LichessDataLoader.parseGames(unknownPgn.split("\n")) should be (empty)
  }

  it should "use 0 for missing or unknown ELO" in {
    val noEloPgn =
      """[Event "Rated Blitz game"]
        |[Site "https://lichess.org/NoEloGame"]
        |[White "Alpha"]
        |[Black "Beta"]
        |[Result "1-0"]
        |[WhiteElo "?"]
        |[TimeControl "180+2"]
        |
        |1. e4 e5 1-0""".stripMargin
    val rows = LichessDataLoader.parseGames(noEloPgn.split("\n"))
    // WhiteElo="?" → blackRow botElo=0; BlackElo missing → whiteRow botElo=0
    rows should have size 2
    rows.find(_._3 == "white").get._5 should be (0)   // botElo = BlackElo (missing)
    rows.find(_._3 == "black").get._5 should be (0)   // botElo = WhiteElo ("?")
  }

  // ── countMoves ──────────────────────────────────────────────────────────────

  "countMoves" should "count 6 plies for the first game's move text" in {
    val moveText =
      "1. e4 { [%clk 0:03:00] } 1... e5 { [%clk 0:03:00] } 2. Nf3 { [%clk 0:02:58] } 2... Nc6 { [%clk 0:02:59] } 3. Bb5 { [%clk 0:02:56] } 3... a6 { [%clk 0:02:57] } 1-0"
    LichessDataLoader.countMoves(moveText) should be (6)
  }

  it should "return 0 for an empty string" in {
    LichessDataLoader.countMoves("") should be (0)
  }

  it should "not count result tokens as moves" in {
    LichessDataLoader.countMoves("1. e4 e5 1-0")    should be (2)
    LichessDataLoader.countMoves("1. e4 e5 0-1")    should be (2)
    LichessDataLoader.countMoves("1. e4 e5 1/2-1/2") should be (2)
    LichessDataLoader.countMoves("1. e4 *")          should be (1)
  }

  // ── loadFromPgn ─────────────────────────────────────────────────────────────

  "loadFromPgn" should "produce a DataFrame with the correct column names" in {
    val path = writeTempPgn()
    val df   = LichessDataLoader.loadFromPgn(spark, path)
    df.columns.toSeq should contain allOf (
      "gameId", "playerName", "playerColor", "winner", "botElo", "moveCount"
    )
  }

  it should "work with victoriesPerPlayer — Magnus has 2 wins" in {
    val path   = writeTempPgn()
    val df     = LichessDataLoader.loadFromPgn(spark, path)
    val result = ChessAnalytics.victoriesPerPlayer(df).collect()
    val map    = result.map(r => r.getString(0) -> r.getLong(1)).toMap
    map("Magnus") should be (2L)
    map.getOrElse("Hikaru", 0L) should be (0L)
    map.getOrElse("DrNykterstein", 0L) should be (0L)
  }

  it should "work with winsPerColor — white=2, black=2" in {
    val path   = writeTempPgn()
    val df     = LichessDataLoader.loadFromPgn(spark, path)
    val result = ChessAnalytics.winsPerColor(df).collect()
    // winsPerColor counts rows where winner != draw, grouped by winner value.
    // Game 1 (winner=white): 2 rows → both counted under "white"
    // Game 2 (winner=black): 2 rows → both counted under "black"
    val map = result.map(r => r.getString(0) -> r.getLong(1)).toMap
    map("white") should be (2L)
    map("black") should be (2L)
  }
}
