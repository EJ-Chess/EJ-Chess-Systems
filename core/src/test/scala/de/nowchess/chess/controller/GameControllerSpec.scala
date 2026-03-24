// core/src/test/scala/de/nowchess/chess/controller/GameControllerSpec.scala
package de.nowchess.chess.controller

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import de.nowchess.chess.model.*

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
