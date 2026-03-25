// core/src/test/scala/de/eljachess/chess/controller/GameControllerSpec.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, Piece, PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import de.eljachess.chess.model.*

class GameControllerSpec extends AnyFlatSpec with Matchers:

  val initial: GameController = GameController(Board.initial)

  "GameController.handleCommand" should "move a piece and return success message" in {
    val (next, msg) = initial.handleCommand("e2 e4")
    msg shouldBe "Moved e2 to e4"
    next.board.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
    next.board.pieceAt(Square(4, 1)) shouldBe None
  }

  it should "return error message and unchanged board on invalid format" in {
    val (next, msg) = initial.handleCommand("not valid")
    msg shouldBe "Invalid command format. Use: <from> <to> (e.g. e2 e4)"
    next.board shouldBe initial.board
  }

  it should "return 'No piece' message and unchanged board when source is empty" in {
    val (next, msg) = initial.handleCommand("e4 e5")
    msg shouldBe "No piece at e4"
    next.board shouldBe initial.board
  }

  it should "not allow Black to move first" in {
    val (next, msg) = initial.handleCommand("e7 e5")
    msg shouldBe "It's White's turn"
    next.board shouldBe initial.board
    next.currentTurn shouldBe Color.White
  }

  it should "switch turn to Black after White moves" in {
    val (next, _) = initial.handleCommand("e2 e4")
    next.currentTurn shouldBe Color.Black
  }

  it should "not allow White to move twice in a row" in {
    val (afterWhite, _) = initial.handleCommand("e2 e4")
    val (afterSecond, msg) = afterWhite.handleCommand("d2 d4")
    msg shouldBe "It's Black's turn"
    afterSecond.currentTurn shouldBe Color.Black
  }

  it should "allow Black to move after White" in {
    val (afterWhite, _) = initial.handleCommand("e2 e4")
    val (afterBlack, msg) = afterWhite.handleCommand("e7 e5")
    msg shouldBe "Moved e7 to e5"
    afterBlack.currentTurn shouldBe Color.White
  }

  it should "return 'Invalid move' when a pawn tries an illegal move" in {
    val (next, msg) = initial.handleCommand("e2 e5")
    msg shouldBe "Invalid move"
    next.board shouldBe initial.board
    next.currentTurn shouldBe Color.White
  }

  it should "reject a move that would leave the own king in check" in {
    // White king e1, white rook e4 pinned by black rook e8
    val board = Board(Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(4, 3) -> Piece(Color.White, PieceKind.Rook),
      Square(4, 7) -> Piece(Color.Black, PieceKind.Rook)
    ))
    val ctrl = GameController(board, Color.White)
    val (next, msg) = ctrl.handleCommand("e4 a4")
    msg shouldBe "Invalid move"
    next.board shouldBe board
  }

  it should "announce check in the move message" in {
    // White rook moves to a7, putting black king at a8 in check
    val board = Board(Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("a1 a7")
    msg should include("Check!")
  }

  it should "announce checkmate in the move message" in {
    // White queen b6, white rook c7 → move Rc7-c8 is checkmate
    val board = Board(Map(
      Square(1, 5) -> Piece(Color.White, PieceKind.Queen),
      Square(2, 6) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("c7 c8")
    msg should include("Checkmate!")
  }

  it should "announce stalemate in the move message" in {
    // White queen c6 → move Qc6-c7 is stalemate for black king at a8
    val board = Board(Map(
      Square(2, 5) -> Piece(Color.White, PieceKind.Queen),
      Square(0, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("c6 c7")
    msg should include("Stalemate!")
  }

  it should "include captured piece info in the message" in {
    // white pawn on e4 captures black pawn on d5; black king on h8 has moves so no stalemate
    val board = Board(Map(
      Square(4, 3) -> Piece(Color.White, PieceKind.Pawn),
      Square(3, 4) -> Piece(Color.Black, PieceKind.Pawn),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("e4 d5")
    msg shouldBe "Moved e4 to d5 – captured Black Pawn"
  }

  it should "include captured White Rook info in the message" in {
    // black rook a8 captures white rook a1; white king on d4 not on same file/rank
    val board = Board(Map(
      Square(0, 7) -> Piece(Color.Black, PieceKind.Rook),
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(3, 3) -> Piece(Color.White, PieceKind.King),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.Black)
    val (_, msg) = ctrl.handleCommand("a8 a1")
    msg shouldBe "Moved a8 to a1 – captured White Rook"
  }

  it should "include captured Black Knight info in the message" in {
    // white rook a1 captures black knight a4
    val board = Board(Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 3) -> Piece(Color.Black, PieceKind.Knight),
      Square(7, 0) -> Piece(Color.White, PieceKind.King),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("a1 a4")
    msg shouldBe "Moved a1 to a4 – captured Black Knight"
  }

  it should "include captured Black Bishop info in the message" in {
    // white rook a4 captures black bishop d4 (same rank, path clear)
    val board = Board(Map(
      Square(0, 3) -> Piece(Color.White, PieceKind.Rook),
      Square(3, 3) -> Piece(Color.Black, PieceKind.Bishop),
      Square(7, 0) -> Piece(Color.White, PieceKind.King),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("a4 d4")
    msg shouldBe "Moved a4 to d4 – captured Black Bishop"
  }

  it should "include captured Black Queen info in the message" in {
    // white rook a1 captures black queen a7; white king on d4 keeps black king with legal moves
    val board = Board(Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 6) -> Piece(Color.Black, PieceKind.Queen),
      Square(3, 3) -> Piece(Color.White, PieceKind.King),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("a1 a7")
    msg shouldBe "Moved a1 to a7 – captured Black Queen"
  }

  it should "include captured Black King info in the message" in {
    // white rook h1 captures black king h8; black has no pieces left → stalemate appended
    val board = Board(Map(
      Square(7, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(7, 7) -> Piece(Color.Black, PieceKind.King),
      Square(0, 0) -> Piece(Color.White, PieceKind.King)
    ))
    val ctrl = GameController(board, Color.White)
    val (_, msg) = ctrl.handleCommand("h1 h8")
    msg should include("captured Black King")
  }
