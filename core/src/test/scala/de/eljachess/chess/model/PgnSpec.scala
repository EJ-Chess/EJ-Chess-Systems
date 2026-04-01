// core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, GameManager, ParsedMove}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PgnSpec extends AnyFlatSpec with Matchers:

  // ── Headers ───────────────────────────────────────────────────────────────

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
    pgn should endWith("*")
  }

  it should "detect checkmate as 0-1 when White to move and checkmated" in {
    // Fool's mate: 1.f3 e5 2.g4 Qh4# — White king is mated
    val (ctrl1, _) = GameController(Board.initial).handleCommand("f2 f3")
    val (ctrl2, _) = ctrl1.handleCommand("e7 e5")
    val (ctrl3, _) = ctrl2.handleCommand("g2 g4")
    val (ctrl4, _) = ctrl3.handleCommand("d8 h4")
    val pgn = Pgn.encode(List(), "White", "Black", ctrl4)
    pgn should include("[Result \"0-1\"]")
    pgn should endWith("0-1")
  }

  it should "detect White wins (1-0) when Black to move and checkmated" in {
    // Black king a8, White queen a1 (check on a-file), White king c7 (blocks escapes)
    val grid = Map(
      Square(0, 7) -> Piece(Color.Black, PieceKind.King),
      Square(0, 0) -> Piece(Color.White, PieceKind.Queen),
      Square(2, 6) -> Piece(Color.White, PieceKind.King)
    )
    val ctrl = GameController(Board(grid), currentTurn = Color.Black)
    val pgn  = Pgn.encode(List(), "White", "Black", ctrl)
    pgn should include("[Result \"1-0\"]")
    pgn should endWith("1-0")
  }

  it should "detect stalemate as 1/2-1/2" in {
    val grid = Map(
      Square(0, 7) -> Piece(Color.Black, PieceKind.King),
      Square(1, 5) -> Piece(Color.White, PieceKind.Queen),
      Square(2, 5) -> Piece(Color.White, PieceKind.King)
    )
    val ctrl = GameController(Board(grid), currentTurn = Color.Black)
    val pgn  = Pgn.encode(List(), "White", "Black", ctrl)
    pgn should include("[Result \"1/2-1/2\"]")
    pgn should endWith("1/2-1/2")
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

  // ── SAN — pawn moves ──────────────────────────────────────────────────────

  "Pgn.sanForMove" should "convert pawn move e2-e4 to \"e4\"" in {
    val board      = Board.initial
    val move       = ParsedMove.Move(Square(4, 1), Square(4, 3), None)
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

  it should "convert pawn promotion e7-e8=Q to \"e8=Q\"" in {
    val grid       = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=Q"
  }

  it should "convert pawn promotion to Rook" in {
    val grid       = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Rook))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Rook)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=R"
  }

  it should "convert pawn promotion to Bishop" in {
    val grid       = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Bishop))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Bishop)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=B"
  }

  it should "convert pawn promotion to Knight" in {
    val grid       = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Knight))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Knight)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=N"
  }

  // ── SAN — piece moves ─────────────────────────────────────────────────────

  it should "convert knight move g1-f3 to \"Nf3\"" in {
    val board      = Board.initial
    val move       = ParsedMove.Move(Square(6, 0), Square(5, 2), None)
    val boardAfter = board.move(Square(6, 0), Square(5, 2), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nf3"
  }

  it should "convert bishop move c1-f4 to \"Bf4\"" in {
    val grid       = Map(Square(2, 0) -> Piece(Color.White, PieceKind.Bishop),
                         Square(7, 7) -> Piece(Color.Black, PieceKind.King))
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(2, 0), Square(5, 3), None)
    val boardAfter = board.move(Square(2, 0), Square(5, 3), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Bf4"
  }

  it should "convert king move e1-f2 to \"Kf2\"" in {
    val grid       = Map(Square(4, 0) -> Piece(Color.White, PieceKind.King),
                         Square(7, 7) -> Piece(Color.Black, PieceKind.King))
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(4, 0), Square(5, 1), None)
    val boardAfter = board.move(Square(4, 0), Square(5, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Kf2"
  }

  it should "include x for non-pawn capture" in {
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

  // ── SAN — castling ────────────────────────────────────────────────────────

  it should "convert kingside castling to \"O-O\"" in {
    Pgn.sanForMove(Board.initial, ParsedMove.Castling(kingside = true), Board.initial) shouldBe "O-O"
  }

  it should "convert queenside castling to \"O-O-O\"" in {
    Pgn.sanForMove(Board.initial, ParsedMove.Castling(kingside = false), Board.initial) shouldBe "O-O-O"
  }

  // ── SAN — check / checkmate annotations ──────────────────────────────────

  it should "append + for moves that give check but not checkmate" in {
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

  it should "append # for moves that give checkmate" in {
    // Fool's mate — Qd8→h4 (h4 = Square(7,3)) delivers checkmate
    val manager = GameManager(GameController(Board.initial))
    manager.move("f2 f3")
    manager.move("e7 e5")
    manager.move("g2 g4")
    val ctrlBefore = manager.state
    val boardAfter = ctrlBefore.board.move(Square(3, 7), Square(7, 3), None).get
    val move       = ParsedMove.Move(Square(3, 7), Square(7, 3), None)
    Pgn.sanForMove(ctrlBefore.board, move, boardAfter) shouldBe "Qh4#"
  }

  // ── SAN — disambiguation ─────────────────────────────────────────────────

  it should "use file disambiguation for pieces on same rank" in {
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

  it should "use file disambiguation for knights on different files" in {
    val grid = Map(
      Square(1, 0) -> Piece(Color.White, PieceKind.Knight),
      Square(5, 2) -> Piece(Color.White, PieceKind.Knight)
    )
    val board      = Board(grid)
    val move       = ParsedMove.Move(Square(1, 0), Square(3, 1), None)
    val boardAfter = board.move(Square(1, 0), Square(3, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nbd2"
  }

  it should "use rank disambiguation for pieces on same file" in {
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

  it should "use full-square disambiguation when pieces share both file and rank" in {
    // Queens on a1, d1 (same rank), a4 (same file) can all reach d4
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

  // ── Exception paths ───────────────────────────────────────────────────────

  it should "throw for non-move commands (FenQuery)" in {
    an[IllegalArgumentException] should be thrownBy {
      Pgn.sanForMove(Board.initial, ParsedMove.FenQuery, Board.initial)
    }
  }

  it should "throw when the from square has no piece" in {
    val board = Board(Map.empty, CastlingRights(false, false, false, false), None)
    val move  = ParsedMove.Move(Square(4, 1), Square(4, 3), None)
    an[IllegalArgumentException] should be thrownBy {
      Pgn.sanForMove(board, move, board)
    }
  }

  it should "throw for invalid promotion piece (Pawn)" in {
    val grid  = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board = Board(grid, CastlingRights(false, false, false, false), None)
    val move  = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Pawn))
    an[IllegalArgumentException] should be thrownBy {
      Pgn.sanForMove(board, move, board)
    }
  }
