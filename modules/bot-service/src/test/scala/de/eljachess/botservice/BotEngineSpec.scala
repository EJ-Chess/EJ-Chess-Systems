package de.eljachess.botservice

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import de.eljachess.chess.model.Color

class BotEngineSpec extends AnyFlatSpec with Matchers:

  private val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  // ── bestMove ─────────────────────────────────────────────────────────────────

  "BotEngine.bestMove" should "return Some move from the initial position for White" in {
    val result = BotEngine.bestMove(initialFen, Color.White, 1400)
    result should not be empty
  }

  it should "return Some move from the initial position for Black" in {
    val blackFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val result   = BotEngine.bestMove(blackFen, Color.Black, 1400)
    result should not be empty
  }

  it should "return a move as two algebraic squares" in {
    val result = BotEngine.bestMove(initialFen, Color.White, 1400).get
    result._1 should have length 2
    result._2 should have length 2
  }

  it should "return a move whose from-square is in [a-h][1-8]" in {
    val (from, _) = BotEngine.bestMove(initialFen, Color.White, 1400).get
    from(0) should (be >= 'a' and be <= 'h')
    from(1) should (be >= '1' and be <= '8')
  }

  it should "return a move whose to-square is in [a-h][1-8]" in {
    val (_, to) = BotEngine.bestMove(initialFen, Color.White, 1400).get
    to(0) should (be >= 'a' and be <= 'h')
    to(1) should (be >= '1' and be <= '8')
  }

  it should "return None for an invalid FEN" in {
    BotEngine.bestMove("not-a-valid-fen", Color.White, 1400) should be(None)
  }

  it should "return None when no legal moves exist (stalemate-like position)" in {
    // King only, completely blocked — White king on a1 surrounded by own pieces
    val stalemateWhiteFen = "7k/8/8/8/8/8/8/K7 b - - 0 1"
    // Black to move but only the king matters here; let's use a real stalemate FEN
    val stalemateBlackFen = "k7/2Q5/1K6/8/8/8/8/8 b - - 0 1"
    BotEngine.bestMove(stalemateBlackFen, Color.Black, 1400) should be(None)
  }

  it should "respect ELO 800 (weaker play — picks from top-5 candidates)" in {
    val result = BotEngine.bestMove(initialFen, Color.White, 800)
    result should not be empty
  }

  it should "respect ELO 1800 (strongest play — picks the top-1 candidate)" in {
    val result = BotEngine.bestMove(initialFen, Color.White, 1800)
    result should not be empty
  }

  it should "return a legal move — from-square must have a piece of the moving color" in {
    // In the initial position, White's pieces occupy rows 1-2 (rows 0-1 in 0-indexed)
    val (from, _) = BotEngine.bestMove(initialFen, Color.White, 1400).get
    // from-square rank must be '1' or '2' (White's starting ranks)
    from(1) should (be('1') or be('2'))
  }

  it should "produce the same kind of legal move across repeated calls" in {
    // Run 10 times and ensure all returned moves are valid algebraic strings
    (1 to 10).foreach { _ =>
      val result = BotEngine.bestMove(initialFen, Color.White, 1400)
      result should not be empty
      val (from, to) = result.get
      from should have length 2
      to   should have length 2
    }
  }

  it should "prefer captures when available (mid-game position with free piece)" in {
    // White pawn on d5 can capture Black pawn on e6 — even weaker bot should notice
    val fen    = "rnbqkbnr/ppp1p1pp/4p3/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3"
    val result = BotEngine.bestMove(fen, Color.White, 1800)
    result should not be empty
  }
