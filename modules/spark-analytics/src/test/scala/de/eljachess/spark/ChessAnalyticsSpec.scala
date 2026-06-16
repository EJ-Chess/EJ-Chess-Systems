package de.eljachess.spark

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for ChessAnalytics aggregation functions.
 *
 * A local[*] SparkSession is created once for the whole suite and torn down
 * in afterAll.  All transformations use in-memory DataFrames — no I/O.
 *
 * Note: ChessKafkaStream.main() is NOT tested here because it requires a live
 * Kafka broker and blocks indefinitely on awaitTermination().
 */
@RunWith(classOf[JUnitRunner])
class ChessAnalyticsSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // `var` needed for BeforeAndAfterAll assignment, but we never re-assign after init.
  @volatile private var spark: SparkSession = _

  override def beforeAll(): Unit =
    spark = SparkSession.builder()
      .master("local[*]")
      .appName("ChessAnalyticsSpec")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "1")
      .getOrCreate()

  override def afterAll(): Unit =
    if (spark != null) spark.stop()

  // ── test fixture ────────────────────────────────────────────────────────────
  //
  // game-001  Alice   white  winner=white  → Alice wins
  // game-002  Bob     black  winner=black  → Bob   wins
  // game-003  Alice   white  winner=draw   → draw  (no winner)
  // game-004  Alice   black  winner=black  → Alice wins
  // game-005  Bob     white  winner=white  → Bob   wins
  // game-006  Charlie white  winner=black  → Charlie loses
  //
  // Alice: 2 wins  |  Bob: 2 wins  |  Charlie: 0 wins

  /** Helper — captures `spark` as a stable val so implicits can be imported. */
  private def withSpark[A](f: SparkSession => A): A = f(spark)

  private def testDf(): DataFrame = withSpark { s =>
    import s.implicits._
    Seq(
      ("game-001", "Alice",   "white", "white", 1200, 24),
      ("game-002", "Bob",     "black", "black", 1400, 32),
      ("game-003", "Alice",   "white", "draw",  1500, 58),
      ("game-004", "Alice",   "black", "black", 1300, 18),
      ("game-005", "Bob",     "white", "white", 1200, 14),
      ("game-006", "Charlie", "white", "black", 1100, 42)
    ).toDF("gameId", "playerName", "playerColor", "winner", "botElo", "moveCount")
  }

  private def allDrawsDf(): DataFrame = withSpark { s =>
    import s.implicits._
    Seq(
      ("g1", "X", "white", "draw", 1000, 10),
      ("g2", "Y", "black", "draw", 1000, 10)
    ).toDF("gameId", "playerName", "playerColor", "winner", "botElo", "moveCount")
  }

  private def emptyDf(): DataFrame = withSpark { s =>
    import s.implicits._
    Seq.empty[(String, String, String, String, Int, Int)]
      .toDF("gameId", "playerName", "playerColor", "winner", "botElo", "moveCount")
  }

  // ── victoriesPerPlayer ──────────────────────────────────────────────────────

  "victoriesPerPlayer" should "count only rows where playerColor matches winner" in {
    val result = ChessAnalytics.victoriesPerPlayer(testDf()).collect()
    val map    = result.map(r => r.getString(0) -> r.getLong(1)).toMap
    map("Alice") should be (2L)
    map("Bob")   should be (2L)
    map.get("Charlie") should be (None)   // Charlie won nothing
  }

  it should "return an empty Dataset when every game is a draw" in {
    ChessAnalytics.victoriesPerPlayer(allDrawsDf()).count() should be (0L)
  }

  it should "return results ordered by victories descending" in withSpark { s =>
    import s.implicits._
    val df = Seq(
      ("g1", "Low",  "white", "white", 1000, 10),
      ("g2", "High", "white", "white", 1000, 10),
      ("g3", "High", "black", "black", 1000, 10)
    ).toDF("gameId", "playerName", "playerColor", "winner", "botElo", "moveCount")

    val rows = ChessAnalytics.victoriesPerPlayer(df).collect()
    rows(0).getString(0) should be ("High")
    rows(1).getString(0) should be ("Low")
  }

  // ── winsPerColor ────────────────────────────────────────────────────────────

  "winsPerColor" should "count wins per color and exclude draws" in {
    val result = ChessAnalytics.winsPerColor(testDf()).collect()
    val map    = result.map(r => r.getString(0) -> r.getLong(1)).toMap
    // white: game-001, game-005 → 2
    // black: game-002, game-004, game-006 → 3
    map("white") should be (2L)
    map("black") should be (3L)
    map.get("draw") should be (None)
  }

  it should "return empty when all games are draws" in {
    ChessAnalytics.winsPerColor(allDrawsDf()).count() should be (0L)
  }

  // ── avgBotEloBeatByPlayer ───────────────────────────────────────────────────

  "avgBotEloBeatByPlayer" should "average botElo across each player's own wins only" in {
    val result = ChessAnalytics.avgBotEloBeatByPlayer(testDf()).collect()
    val map    = result.map(r => r.getString(0) -> r.getDouble(1)).toMap
    // Alice wins: elo 1200 (game-001) + 1300 (game-004) → avg 1250
    map("Alice") should be (1250.0 +- 0.01)
    // Bob wins:   elo 1400 (game-002) + 1200 (game-005) → avg 1300
    map("Bob")   should be (1300.0 +- 0.01)
    map.get("Charlie") should be (None)
  }

  it should "return empty when no player won" in {
    ChessAnalytics.avgBotEloBeatByPlayer(allDrawsDf()).count() should be (0L)
  }

  // ── bestPlayer ──────────────────────────────────────────────────────────────

  "bestPlayer" should "return a player name from the top of the victories table" in {
    // Alice and Bob are tied with 2 wins each; either is acceptable
    val best = ChessAnalytics.bestPlayer(testDf())
    best should (equal("Alice") or equal("Bob"))
  }

  it should "return 'No games played' when the dataset is empty" in {
    ChessAnalytics.bestPlayer(emptyDf()) should be ("No games played")
  }

  it should "return 'No games played' when all games are draws" in {
    ChessAnalytics.bestPlayer(allDrawsDf()) should be ("No games played")
  }
}
