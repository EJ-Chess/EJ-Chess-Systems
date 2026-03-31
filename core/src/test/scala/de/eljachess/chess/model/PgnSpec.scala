// core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, ParsedMove}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PgnSpec extends AnyFlatSpec with Matchers:

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
    val pgn = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should include(s"[Date \"$today\"]")
  }

  it should "detect in-progress game as result *" in {
    val pgn = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should include("[Result \"*\"]")
    pgn should endWith("*")
  }

  it should "detect checkmate as 1-0 when Black to move and checkmated" in {
    // Fool's mate: 1.f3 e5 2.g4 Qh4# — White is checkmated, result is 0-1
    val (ctrl1, _) = GameController(Board.initial).handleCommand("f2 f3")
    val (ctrl2, _) = ctrl1.handleCommand("e7 e5")
    val (ctrl3, _) = ctrl2.handleCommand("g2 g4")
    val (ctrl4, _) = ctrl3.handleCommand("d8 h4")
    val pgn = Pgn.encode(List(), "White", "Black", ctrl4)
    pgn should include("[Result \"0-1\"]")
    pgn should endWith("0-1")
  }

  it should "detect stalemate as 1/2-1/2" in {
    // Black king on a8, white queen on b6, white king on h1 — Black is stalemated
    val fenStr = "k7/8/1Q6/8/8/8/8/7K b - - 0 1"
    val Right(stalemateCtrl) = Fen.decode(fenStr)
    val pgn = Pgn.encode(List(), "White", "Black", stalemateCtrl)
    pgn should include("[Result \"1/2-1/2\"]")
    pgn should endWith("1/2-1/2")
  }

  // ── SAN generation ─────────────────────────────────────────────────────

  "Pgn.sanForMove" should "convert pawn move e2-e4 to SAN \"e4\"" in {
    val board = Board.initial
    val move = ParsedMove.Move(Square(4, 1), Square(4, 3), None)
    val boardAfter = board.move(Square(4, 1), Square(4, 3), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e4"
  }

  it should "convert pawn capture e4xd5 to SAN \"exd5\"" in {
    val grid = Map(
      Square(4, 3) -> Piece(Color.White, PieceKind.Pawn),
      Square(3, 4) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val board = Board(grid)
    val move = ParsedMove.Move(Square(4, 3), Square(3, 4), None)
    val boardAfter = board.move(Square(4, 3), Square(3, 4), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "exd5"
  }

  it should "convert en-passant capture to correct SAN" in {
    // White pawn on e5, black pawn on d5 — en passant target is d6
    val grid = Map(
      Square(4, 4) -> Piece(Color.White, PieceKind.Pawn),
      Square(3, 4) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val castling = CastlingRights(false, false, false, false)
    val boardWithEP = Board(grid, castling, Some(Square(3, 5)))  // d6 is en-passant target
    val move = ParsedMove.Move(Square(4, 4), Square(3, 5), None) // e5xd6 e.p.
    val boardAfter = boardWithEP.move(Square(4, 4), Square(3, 5), None).get
    Pgn.sanForMove(boardWithEP, move, boardAfter) shouldBe "exd6"
  }

  it should "convert knight move g1-f3 to SAN \"Nf3\"" in {
    val board = Board.initial
    val move = ParsedMove.Move(Square(6, 0), Square(5, 2), None)
    val boardAfter = board.move(Square(6, 0), Square(5, 2), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nf3"
  }

  it should "convert piece capture Nf3xe5 to SAN \"Nxe5\"" in {
    val grid = Map(
      Square(5, 2) -> Piece(Color.White, PieceKind.Knight),
      Square(4, 4) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val board = Board(grid)
    val move = ParsedMove.Move(Square(5, 2), Square(4, 4), None)
    val boardAfter = board.move(Square(5, 2), Square(4, 4), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nxe5"
  }

  it should "convert castling kingside to SAN \"O-O\"" in {
    val board = Board.initial
    val move = ParsedMove.Castling(kingside = true)
    Pgn.sanForMove(board, move, board) shouldBe "O-O"
  }

  it should "convert castling queenside to SAN \"O-O-O\"" in {
    val board = Board.initial
    val move = ParsedMove.Castling(kingside = false)
    Pgn.sanForMove(board, move, board) shouldBe "O-O-O"
  }

  it should "convert pawn promotion e7-e8=Q to SAN \"e8=Q\"" in {
    val grid = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board = Board(grid)
    val move = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=Q"
  }

  it should "format empty move list as empty string" in {
    val pgn = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    pgn should not include "1."
  }

  it should "append check symbol + to SAN when move gives check" in {
    // Knight moves e4 (4,3) -> f6 (5,5); attacks e8 (4,7) where Black king stands
    // Knight on f6 attacks e8: |dc|=1, |dr|=2 — valid knight attack
    val grid = Map(
      Square(4, 3) -> Piece(Color.White, PieceKind.Knight),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board = Board(grid)
    val move = ParsedMove.Move(Square(4, 3), Square(5, 5), None)
    val boardAfter = board.move(Square(4, 3), Square(5, 5), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nf6+"
  }

  it should "append checkmate symbol # to SAN for Fool's mate Qh4" in {
    // Fool's mate: 1.f3 e5 2.g4 Qh4#
    val (ctrl1, _) = GameController(Board.initial).handleCommand("f2 f3")
    val (ctrl2, _) = ctrl1.handleCommand("e7 e5")
    val (ctrl3, _) = ctrl2.handleCommand("g2 g4")
    val (ctrl4, _) = ctrl3.handleCommand("d8 h4")
    // board before Qh4
    val board = ctrl3.board
    val move = ParsedMove.Move(Square(3, 7), Square(7, 3), None)
    val boardAfter = ctrl4.board
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Qh4#"
  }

  // ── detectResult 1-0 ──────────────────────────────────────────────────

  "Pgn.encode" should "detect checkmate as 1-0 when White checkmated Black" in {
    // k1R5/8/KQ6/8/8/8/8/8 b - - 0 1
    // Black king a8 is in check from rook c8; no legal escape
    val fenStr = "k1R5/8/KQ6/8/8/8/8/8 b - - 0 1"
    val Right(ctrl) = Fen.decode(fenStr)
    val pgn = Pgn.encode(List(), "White", "Black", ctrl)
    pgn should include("[Result \"1-0\"]")
    pgn should endWith("1-0")
  }

  // ── Piece move SAN (Rook, Bishop, Knight) ────────────────────────────

  it should "convert rook move a1-a5 to SAN \"Ra5\"" in {
    val grid = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(0, 0), Square(0, 4), None)
    val boardAfter = board.move(Square(0, 0), Square(0, 4), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Ra5"
  }

  it should "convert bishop move c1-f4 to SAN \"Bf4\"" in {
    val grid = Map(
      Square(2, 0) -> Piece(Color.White, PieceKind.Bishop),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(2, 0), Square(5, 3), None)
    val boardAfter = board.move(Square(2, 0), Square(5, 3), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Bf4"
  }

  // ── Disambiguation by rank (same file) ──────────────────────────────

  it should "disambiguate by rank when two rooks share the same file" in {
    // Ra1 and Ra3 both can move to a2; use rank digit to disambiguate
    val grid = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 2) -> Piece(Color.White, PieceKind.Rook)
    )
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(0, 0), Square(0, 1), None)
    val boardAfter = board.move(Square(0, 0), Square(0, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "R1a2"
  }

  // ── Promotion pieceChar (Rook, Bishop, Knight) ───────────────────────

  it should "convert pawn promotion e7-e8=R to SAN \"e8=R\"" in {
    val grid = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Rook))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Rook)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=R"
  }

  it should "convert pawn promotion e7-e8=B to SAN \"e8=B\"" in {
    val grid = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Bishop))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Bishop)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=B"
  }

  it should "convert pawn promotion e7-e8=N to SAN \"e8=N\"" in {
    val grid = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Knight))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Knight)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=N"
  }

  it should "convert king move e1-f2 to SAN \"Kf2\"" in {
    val grid = Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(4, 0), Square(5, 1), None)
    val boardAfter = board.move(Square(4, 0), Square(5, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Kf2"
  }

  it should "disambiguate by file when two knights on different files can reach the same square" in {
    // Nb1 and Nf3 can both reach d2; use file letter 'b' for the b1 knight
    val grid = Map(
      Square(1, 0) -> Piece(Color.White, PieceKind.Knight),
      Square(5, 2) -> Piece(Color.White, PieceKind.Knight)
    )
    val board = Board(grid)
    val move  = ParsedMove.Move(Square(1, 0), Square(3, 1), None)
    val boardAfter = board.move(Square(1, 0), Square(3, 1), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nbd2"
  }

  // ── Exception paths ─────────────────────────────────────────────────────

  "Pgn.sanForMove" should "throw when a non-move ParsedMove (FenQuery) is passed" in {
    val board = Board.initial
    a [Exception] should be thrownBy {
      Pgn.sanForMove(board, ParsedMove.FenQuery, board)
    }
  }

  it should "throw when the from square has no piece" in {
    // Empty board — no piece at any square
    val board = Board(Map.empty, CastlingRights(false, false, false, false), None)
    val move = ParsedMove.Move(Square(4, 1), Square(4, 3), None)
    a [Exception] should be thrownBy {
      Pgn.sanForMove(board, move, board)
    }
  }

  it should "throw for invalid promotion piece kind (Pawn)" in {
    val grid = Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn))
    val board = Board(grid, CastlingRights(false, false, false, false), None)
    val move = ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Pawn))
    a [Exception] should be thrownBy {
      Pgn.sanForMove(board, move, board)
    }
  }
