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
