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

  it should "detect checkmate as 1-0 when Black to move and checkmated" is (pending)

  it should "detect stalemate as 1/2-1/2" is (pending)

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

  it should "convert knight move g1-f3 to SAN \"Nf3\"" in {
    val board = Board.initial
    val move = ParsedMove.Move(Square(6, 0), Square(5, 2), None)
    val boardAfter = board.move(Square(6, 0), Square(5, 2), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nf3"
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
    pgn should not include " 0."
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
