package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, Piece, PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GameManagerSpec extends AnyFlatSpec with Matchers:

  private class MockObserver extends Observer:
    var updates: List[(GameController, String)] = Nil
    def onUpdate(ctrl: GameController, msg: String): Unit =
      updates = (ctrl, msg) :: updates

  private def freshManager: GameManager =
    GameManager(GameController(Board.initial))

  "GameManager.move" should "notify all observers on a valid move" in {
    val manager = freshManager
    val obs = MockObserver()
    manager.addObserver(obs)
    manager.move("e2 e4")
    obs.updates should have size 1
    obs.updates.head._2 shouldBe "Moved e2 to e4"
  }

  it should "not notify observers on an invalid move" in {
    val manager = freshManager
    val obs = MockObserver()
    manager.addObserver(obs)
    manager.move("e2 e5") // illegal pawn jump
    obs.updates shouldBe empty
  }

  it should "skip the caller observer on a valid move" in {
    val manager = freshManager
    val obs1 = MockObserver()
    val obs2 = MockObserver()
    manager.addObserver(obs1)
    manager.addObserver(obs2)
    manager.move("e2 e4", obs1)
    obs1.updates shouldBe empty
    obs2.updates should have size 1
  }

  it should "update state after a valid move" in {
    val manager = freshManager
    manager.move("e2 e4")
    manager.state.board.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
  }

  it should "return the message from handleCommand" in {
    val manager = freshManager
    val msg = manager.move("e2 e4")
    msg shouldBe "Moved e2 to e4"
  }

  it should "notify multiple observers (except caller)" in {
    val manager = freshManager
    val obs1 = MockObserver()
    val obs2 = MockObserver()
    val obs3 = MockObserver()
    manager.addObserver(obs1)
    manager.addObserver(obs2)
    manager.addObserver(obs3)
    manager.move("e2 e4", obs2)
    obs1.updates should have size 1
    obs2.updates shouldBe empty
    obs3.updates should have size 1
  }

  "GameManager.undo" should "restore the previous state" in {
    val manager = freshManager
    val initial = manager.state
    manager.move("e2 e4")
    manager.undo()
    manager.state shouldBe initial
  }

  it should "notify observers with 'Undo' message" in {
    val manager = freshManager
    manager.move("e2 e4")
    val obs = MockObserver()
    manager.addObserver(obs)
    manager.undo()
    obs.updates should have size 1
    obs.updates.head._2 shouldBe "Undo"
  }

  it should "return 'Nothing to undo' on empty history without notifying" in {
    val manager = freshManager
    val obs = MockObserver()
    manager.addObserver(obs)
    manager.undo() shouldBe "Nothing to undo"
    obs.updates shouldBe empty
  }

  it should "skip caller on undo" in {
    val manager = freshManager
    manager.move("e2 e4")
    val obs1 = MockObserver()
    val obs2 = MockObserver()
    manager.addObserver(obs1)
    manager.addObserver(obs2)
    manager.undo(obs1)
    obs1.updates shouldBe empty
    obs2.updates should have size 1
  }

  it should "skip caller on redo" in {
    val manager = freshManager
    manager.move("e2 e4")
    manager.undo()
    val obs1 = MockObserver()
    val obs2 = MockObserver()
    manager.addObserver(obs1)
    manager.addObserver(obs2)
    manager.redo(obs1)
    obs1.updates shouldBe empty
    obs2.updates should have size 1
  }

  "GameManager.redo" should "restore the undone state" in {
    val manager = freshManager
    manager.move("e2 e4")
    val stateAfterMove = manager.state
    manager.undo()
    manager.redo()
    manager.state shouldBe stateAfterMove
  }

  it should "return 'Nothing to redo' on empty future without notifying" in {
    val manager = freshManager
    val obs = MockObserver()
    manager.addObserver(obs)
    manager.redo() shouldBe "Nothing to redo"
    obs.updates shouldBe empty
  }

  it should "clear the redo stack after a new move" in {
    val manager = freshManager
    manager.move("e2 e4")
    manager.undo()
    manager.move("d2 d4")
    manager.redo() shouldBe "Nothing to redo"
  }
