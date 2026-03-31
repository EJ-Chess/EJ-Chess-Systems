package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, Piece, PieceKind, Pgn, Square}
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

  "GameManager PGN replay" should "replay a 2-move game e4 e5 correctly" in {
    val manager = new GameManager(GameController(Board.initial))
    val (_, moves) = Pgn.decode("1. e4 e5").getOrElse((Map.empty, List.empty))
    moves shouldBe List("e4", "e5")

    moves.foreach { san =>
      val ctrl = manager.state
      SanDecoder.expand(ctrl.board, ctrl.currentTurn, san) match
        case Left(err) => fail(s"SAN expansion failed: $err")
        case Right((from, to, _)) =>
          manager.move(s"${from.toAlgebraic} ${to.toAlgebraic}")
    }

    manager.state.board.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
    manager.state.board.pieceAt(Square(4, 4)) shouldBe Some(Piece(Color.Black, PieceKind.Pawn))
    manager.state.currentTurn shouldBe Color.White
  }

  it should "replay promotion and create Queen on e8" is pending

  it should "replay O-O and place king on g1 and rook on f1" is pending

  it should "stop at first illegal SAN move and leave board at last valid position" in {
    val manager = new GameManager(GameController(Board.initial))
    val sanMoves = List("e4", "e5", "Xe6")  // Xe6 is invalid SAN

    var stopped = false
    var halfmove = 1
    sanMoves.foreach { san =>
      if !stopped then
        val ctrl = manager.state
        SanDecoder.expand(ctrl.board, ctrl.currentTurn, san) match
          case Left(_) => stopped = true
          case Right((from, to, _)) =>
            manager.move(s"${from.toAlgebraic} ${to.toAlgebraic}")
            halfmove += 1
    }

    stopped shouldBe true
    halfmove shouldBe 3  // stopped at the 3rd move
    // board should be at position after e4 e5 (2 halfmoves played)
    manager.state.board.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
    manager.state.board.pieceAt(Square(4, 4)) shouldBe Some(Piece(Color.Black, PieceKind.Pawn))
  }
