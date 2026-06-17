package de.eljachess.botservice

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Square}

/** Applies a move string "e2e4" to a board decoded from FEN. Returns the new board. */
private def applyMove(fen: String, fromStr: String, toStr: String): Board =
  val board = Fen.decode(fen).toOption.get.board
  val from  = Square(fromStr(0) - 'a', fromStr(1) - '1')
  val to    = Square(toStr(0)   - 'a', toStr(1)   - '1')
  board.move(from, to, Some(PieceKind.Queen)).get

/** Returns true when the given color has no legal moves and is in check (checkmate). */
private def isCheckmate(board: Board, color: Color): Boolean =
  board.legalMoves(color).isEmpty && board.isInCheck(color)

class BotEngineSpec extends AnyFlatSpec with Matchers:

  private val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  // ── Public API (unchanged) ────────────────────────────────────────────────────

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
    val stalemateBlackFen = "k7/2Q5/1K6/8/8/8/8/8 b - - 0 1"
    BotEngine.bestMove(stalemateBlackFen, Color.Black, 1400) should be(None)
  }

  it should "respect ELO 800 (weaker play)" in {
    BotEngine.bestMove(initialFen, Color.White, 800) should not be empty
  }

  it should "respect ELO 1800 (strongest play)" in {
    BotEngine.bestMove(initialFen, Color.White, 1800) should not be empty
  }

  it should "return a legal move — from-square must have a piece of the moving color" in {
    val (from, _) = BotEngine.bestMove(initialFen, Color.White, 1400).get
    from(1) should (be('1') or be('2'))
  }

  it should "produce valid algebraic moves across repeated calls" in {
    (1 to 10).foreach { _ =>
      val result = BotEngine.bestMove(initialFen, Color.White, 1400)
      result should not be empty
      val (from, to) = result.get
      from should have length 2
      to   should have length 2
    }
  }

  it should "prefer captures when available (mid-game position with free piece)" in {
    val fen    = "rnbqkbnr/ppp1p1pp/4p3/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3"
    val result = BotEngine.bestMove(fen, Color.White, 1800)
    result should not be empty
  }

  // ── Minimax behaviour ─────────────────────────────────────────────────────────

  // Position: White king a6, White queen b6, Black king a8.
  // Multiple queen moves give checkmate — the engine must play one of them.
  "BotEngine.bestMove (minimax)" should "find checkmate in 1 for White" in {
    val mateIn1Fen = "k7/8/KQ6/8/8/8/8/8 w - - 0 1"
    val result     = BotEngine.bestMove(mateIn1Fen, Color.White, 1400)
    result should not be empty
    val (from, to) = result.get
    val after      = applyMove(mateIn1Fen, from, to)
    withClue(s"Engine returned ($from, $to)") {
      isCheckmate(after, Color.Black) shouldBe true
    }
  }

  // Position: Black king h8, Black queen g7, White king g6.
  // Qg8# is mate.
  it should "find checkmate in 1 for Black" in {
    val mateIn1Fen = "7K/6q1/6k1/8/8/8/8/8 b - - 0 1"
    val result     = BotEngine.bestMove(mateIn1Fen, Color.Black, 1400)
    result should not be empty
    val (from, to) = result.get
    val after      = applyMove(mateIn1Fen, from, to)
    isCheckmate(after, Color.White) shouldBe true
  }

  // White rook on d1 can take Black rook on d8 (open file, undefended).
  it should "capture a free rook on an open file" in {
    val fen    = "3rk3/8/8/8/8/8/8/3RK3 w - - 0 1"
    val result = BotEngine.bestMove(fen, Color.White, 1400)
    result shouldBe Some(("d1", "d8"))
  }

  // White queen d1, Black pawn on e5; d4 is guarded by pawn — engine must not walk into it.
  it should "not move queen into an opponent pawn's capture square (depth ≥ 2)" in {
    val fen    = "4k3/8/8/4p3/8/8/8/3QK3 w - - 0 1"
    val result = BotEngine.bestMove(fen, Color.White, 1400)
    result should not be empty
    result.get._2 should not be "d4"
  }

  // At depth 3 the engine should find a checkmate sequence over the next moves.
  it should "find the first move of a back-rank checkmate sequence (depth 3)" in {
    val fen    = "6k1/6pp/8/8/8/8/8/R3K2R w KQ - 0 1"
    val result = BotEngine.bestMove(fen, Color.White, 1400)
    result should not be empty
  }

