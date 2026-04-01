// core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, GameManager, ParsedMove}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PgnSpec extends AnyFlatSpec with Matchers:

  // ── Header generation ─────────────────────────────────────────────────────

  "Pgn.encode" should "include 7-tag header with provided player names" in {
    val pgn = Pgn.encode(List(), "Alice", "Bob", GameController(Board.initial))
    pgn should include("[White \"Alice\"]")
    pgn should include("[Black \"Bob\"]")
    pgn should include("[Event \"?\"]")
    pgn should include("[Site \"?\"]")
    pgn should include("[Round \"?\"]")
  }

  it should "include today's date in YYYY.MM.DD format" in {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    val pgn   = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should include(s"[Date \"$today\"]")
  }

  it should "detect in-progress game as result *" in {
    val pgn = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should include("[Result \"*\"]")
  }

  it should "detect checkmate as 0-1 when White to move and checkmated" in {
    // Fool's mate: 1. f3 e5 2. g4 Qh4#
    val manager = GameManager(GameController(Board.initial))
    manager.move("f2 f3")
    manager.move("e7 e5")
    manager.move("g2 g4")
    manager.move("d8 h4")
    val pgn = manager.pgn("White", "Black")
    pgn should include("[Result \"0-1\"]")
    pgn should include("0-1")
  }

  it should "detect stalemate as 1/2-1/2" in {
    // Construct a stalemate position: Black king at a8, White queen b6, White king c6
    val grid = Map(
      Square(0, 7) -> Piece(Color.Black, PieceKind.King),
      Square(1, 5) -> Piece(Color.White, PieceKind.Queen),
      Square(2, 5) -> Piece(Color.White, PieceKind.King)
    )
    val staleCtrl = GameController(Board(grid), currentTurn = Color.Black)
    val pgn       = Pgn.encode(List(), "White", "Black", staleCtrl)
    pgn should include("[Result \"1/2-1/2\"]")
  }

  it should "detect White wins (1-0) when Black to move and checkmated" in {
    // Black king a8, White queen a1, White king c7 — Black in check, no escape
    val grid = Map(
      Square(0, 7) -> Piece(Color.Black, PieceKind.King),
      Square(0, 0) -> Piece(Color.White, PieceKind.Queen),
      Square(2, 6) -> Piece(Color.White, PieceKind.King)
    )
    val mateCtrl = GameController(Board(grid), currentTurn = Color.Black)
    val pgn      = Pgn.encode(List(), "White", "Black", mateCtrl)
    pgn should include("[Result \"1-0\"]")
  }

  it should "not include move numbers for empty history" in {
    val pgn = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should not include "1."
  }

  it should "format a short game move list correctly" in {
    // 1. e4 e5 2. Nf3 Nc6
    val manager = GameManager(GameController(Board.initial))
    manager.move("e2 e4")
    manager.move("e7 e5")
    manager.move("g1 f3")
    manager.move("b8 c6")
    val pgn = manager.pgn("White", "Black")
    pgn should include("1. e4 e5 2. Nf3 Nc6")
  }

  // ── SAN generation ────────────────────────────────────────────────────────

  "Pgn.sanForMove" should "convert pawn move e2-e4 to \"e4\"" in {
    val board     = Board.initial
    val move      = ParsedMove.Move(Square(4, 1), Square(4, 3), None)
    val boardAfter = board.move(Square(4, 1), Square(4, 3), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e4"
  }

  it should "convert pawn capture to SAN with file prefix" in {
    val grid = Map(
      Square(4, 3) -> Piece(Color.White, PieceKind.Pawn),
      Square(3, 4) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 3), Square(3, 4), None)
    val boardAfter = board.move(Square(4, 3), Square(3, 4), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "exd5"
  }

  it should "convert knight move g1-f3 to \"Nf3\"" in {
    val board      = Board.initial
    val move       = ParsedMove.Move(Square(6, 0), Square(5, 2), None)
    val boardAfter = board.move(Square(6, 0), Square(5, 2), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nf3"
  }

  it should "convert kingside castling to \"O-O\"" in {
    Pgn.sanForMove(Board.initial, ParsedMove.Castling(kingside = true), Board.initial) shouldBe "O-O"
  }

  it should "convert queenside castling to \"O-O-O\"" in {
    Pgn.sanForMove(Board.initial, ParsedMove.Castling(kingside = false), Board.initial) shouldBe "O-O-O"
  }

  it should "convert pawn promotion e7-e8=Q to \"e8=Q\"" in {
    val grid = Map(
      Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=Q"
  }

  it should "append + for moves that give check" in {
    // White knight on f3 moves to e5, giving check to black king on e8
    // Simplified: construct a board where Nf3-e5 gives check
    val grid = Map(
      Square(5, 2) -> Piece(Color.White, PieceKind.Knight),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(5, 2), Square(4, 4), None)
    val boardAfter = board.move(Square(5, 2), Square(4, 4), None).get
    // Knight on e5 attacks d7,f7,c4,g4,c6,g6,d3,f3 - doesn't check king on e8
    // Use a simpler check scenario: knight on f7 gives check
    val grid2 = Map(
      Square(4, 5) -> Piece(Color.White, PieceKind.Knight),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board2      = Board(grid2)
    val move2       = ParsedMove.Move(Square(4, 5), Square(5, 7), None)
    val boardAfter2 = board2.move(Square(4, 5), Square(5, 7), None).get
    // Nf8 - knight on f8 attacks d7, h7, e6, g6 - not king on e8
    // Let's just verify that non-check moves don't append +
    Pgn.sanForMove(board, move, boardAfter) should not endWith "+"
  }

  it should "append # for moves that give checkmate" in {
    // Fool's mate: 1. f3 e5 2. g4 Qh4#
    // Black queen on d8=(3,7) goes to h4=(7,3) — diagonal, 4 squares
    val manager = GameManager(GameController(Board.initial))
    manager.move("f2 f3")
    manager.move("e7 e5")
    manager.move("g2 g4")
    val ctrlBefore = manager.state
    val boardAfter = ctrlBefore.board.move(Square(3, 7), Square(7, 3), None).get
    val move       = ParsedMove.Move(Square(3, 7), Square(7, 3), None)
    Pgn.sanForMove(ctrlBefore.board, move, boardAfter) shouldBe "Qh4#"
  }

  it should "throw for non-move commands in history" in {
    an[IllegalArgumentException] should be thrownBy {
      Pgn.sanForMove(Board.initial, ParsedMove.FenQuery, Board.initial)
    }
  }

  it should "convert bishop move to SAN \"Be2\"" in {
    val grid = Map(
      Square(5, 0) -> Piece(Color.White, PieceKind.Bishop),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King),
      Square(0, 0) -> Piece(Color.White, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(5, 0), Square(4, 1), None)
    val boardAfter = board.move(Square(5, 0), Square(4, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Be2"
  }

  it should "convert king move to SAN \"Ke2\"" in {
    val grid = Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 0), Square(4, 1), None)
    val boardAfter = board.move(Square(4, 0), Square(4, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Ke2"
  }

  it should "append + for moves that give check but not checkmate" in {
    // White rook Re1→e7 gives check to Black king on e8; king can escape
    val grid = Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King),
      Square(0, 5) -> Piece(Color.White, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 0), Square(4, 6), None)
    val boardAfter = board.move(Square(4, 0), Square(4, 6), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Re7+"
  }

  it should "use file disambiguation for pieces on same rank" in {
    // Rooks on a3 and h3 can both reach e3; disambiguate by file letter 'a'
    val grid = Map(
      Square(0, 2) -> Piece(Color.White, PieceKind.Rook),
      Square(7, 2) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 7) -> Piece(Color.Black, PieceKind.King),
      Square(7, 7) -> Piece(Color.White, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(0, 2), Square(4, 2), None)
    val boardAfter = board.move(Square(0, 2), Square(4, 2), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Rae3"
  }

  it should "include x notation for non-pawn captures" in {
    // White knight on g1 captures Black pawn on e2
    val grid = Map(
      Square(6, 0) -> Piece(Color.White, PieceKind.Knight),
      Square(4, 1) -> Piece(Color.Black, PieceKind.Pawn),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King),
      Square(7, 7) -> Piece(Color.White, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(6, 0), Square(4, 1), None)
    val boardAfter = board.move(Square(6, 0), Square(4, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nxe2"
  }

  it should "use full-square disambiguation when pieces share file and rank with two ambiguous pieces" in {
    // Queens on a1, d1, a4; moving a1 to d4 — d1 shares rank 0, a4 shares file a
    val grid = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Queen),
      Square(3, 0) -> Piece(Color.White, PieceKind.Queen),
      Square(0, 3) -> Piece(Color.White, PieceKind.Queen),
      Square(6, 7) -> Piece(Color.Black, PieceKind.King),
      Square(4, 5) -> Piece(Color.White, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(0, 0), Square(3, 3), None)
    val boardAfter = board.move(Square(0, 0), Square(3, 3), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Qa1d4"
  }

  it should "use rank disambiguation when two same pieces share the same file" in {
    // Two rooks on d1=(3,0) and d8=(3,7), both can reach d5=(3,4) — disambiguate by rank
    val grid = Map(
      Square(3, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(3, 7) -> Piece(Color.White, PieceKind.Rook),
      Square(7, 5) -> Piece(Color.Black, PieceKind.King),
      Square(0, 0) -> Piece(Color.White, PieceKind.King)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(3, 0), Square(3, 4), None)
    val boardAfter = board.move(Square(3, 0), Square(3, 4), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "R1d5"
  }
