package de.eljachess.chess.tui

import de.eljachess.chess.controller.{GameController, GameManager, Observer}
import de.eljachess.chess.model.{Board, Color, Json, Piece, PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.{ByteArrayOutputStream, PrintStream}

class TUISpec extends AnyFlatSpec with Matchers:

  /** Captures Scala Console output produced by f using Console.withOut (thread-local, no global mutation). */
  private def captureOutput(f: => Unit): String =
    val buf = ByteArrayOutputStream()
    val ps  = PrintStream(buf, true, "UTF-8")
    Console.withOut(ps)(f)
    buf.toString("UTF-8")

  private def makeReadLine(inputs: String*): () => String | Null =
    val it = inputs.iterator
    () => if it.hasNext then it.next() else null

  private def freshManager = GameManager(GameController(Board.initial))

  "TUI.onUpdate" should "print [GUI] prefix followed by the message" in {
    val manager = freshManager
    val tui     = TUI(manager)
    val out = captureOutput:
      tui.onUpdate(GameController(Board.initial), "test message")
    out should include("[GUI] test message")
  }

  it should "render the board after printing [GUI] message" in {
    val manager = freshManager
    val tui     = TUI(manager)
    val out = captureOutput:
      tui.onUpdate(GameController(Board.initial), "Moved e2 to e4")
    out should include("White's turn")
  }

  "TUI loop" should "call manager.move for a chess command" in {
    val manager = freshManager
    val tui = TUI(manager, makeReadLine("e2 e4"))
    captureOutput { tui.start() }
    manager.state.board.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
  }

  it should "call manager.undo for 'undo' input" in {
    val manager = freshManager
    val initial = manager.state
    val tui = TUI(manager, makeReadLine("e2 e4", "undo"))
    captureOutput { tui.start() }
    manager.state shouldBe initial
  }

  it should "call manager.undo for uppercase 'UNDO' input" in {
    val manager = freshManager
    val initial = manager.state
    val tui = TUI(manager, makeReadLine("e2 e4", "UNDO"))
    captureOutput { tui.start() }
    manager.state shouldBe initial
  }

  it should "call manager.redo for 'redo' input" in {
    val manager = freshManager
    manager.move("e2 e4")
    val stateAfterMove = manager.state
    manager.undo()
    val tui = TUI(manager, makeReadLine("redo"))
    captureOutput { tui.start() }
    manager.state shouldBe stateAfterMove
  }

  it should "call manager.redo for uppercase 'REDO' input" in {
    val manager = freshManager
    manager.move("e2 e4")
    val stateAfterMove = manager.state
    manager.undo()
    val tui = TUI(manager, makeReadLine("REDO"))
    captureOutput { tui.start() }
    manager.state shouldBe stateAfterMove
  }

  it should "ignore empty lines without calling manager" in {
    val manager = freshManager
    val initial = manager.state
    val tui = TUI(manager, makeReadLine("", "   "))
    captureOutput { tui.start() }
    manager.state shouldBe initial
  }

  it should "stop when readLine returns null (EOF)" in {
    val manager = freshManager
    val tui = TUI(manager, makeReadLine())  // immediately returns null
    captureOutput { tui.start() }           // must not hang
    succeed
  }

  it should "save game to JSON with save-json <filename> command" in {
    val manager = freshManager
    manager.move("e2 e4")
    val tmp = java.nio.file.Files.createTempFile("chess-test", ".json")
    try
      val tui = TUI(manager, makeReadLine(s"save-json ${tmp.toString}"))
      captureOutput { tui.start() }
      java.nio.file.Files.size(tmp) should be > 0L
    finally
      java.nio.file.Files.deleteIfExists(tmp)
  }

  it should "load game from JSON with load-json <filename> command" in {
    val json = Json.encode(GameController(Board.initial))
    val tmp = java.nio.file.Files.createTempFile("chess-load", ".json")
    try
      java.nio.file.Files.write(tmp, json.getBytes("UTF-8"))
      val manager = freshManager
      manager.move("e2 e4")
      val tui = TUI(manager, makeReadLine(s"load-json ${tmp.toString}"))
      captureOutput { tui.start() }
      manager.state.board shouldBe Board.initial
    finally
      java.nio.file.Files.deleteIfExists(tmp)
  }

  it should "print error on load-json with missing file" in {
    val manager = freshManager
    val tui = TUI(manager, makeReadLine("load-json /this/does/not/exist.json"))
    val out = captureOutput { tui.start() }
    out should include("Error")
  }

  it should "print 'Saved to' message after save-json" in {
    val tmp = java.nio.file.Files.createTempFile("chess-msg", ".json")
    try
      val manager = freshManager
      val tui = TUI(manager, makeReadLine(s"save-json ${tmp.toString}"))
      val out = captureOutput { tui.start() }
      out should include("Saved to")
    finally
      java.nio.file.Files.deleteIfExists(tmp)
  }

  it should "print 'Loaded from' message after load-json" in {
    val json = Json.encode(GameController(Board.initial))
    val tmp = java.nio.file.Files.createTempFile("chess-msg", ".json")
    try
      java.nio.file.Files.write(tmp, json.getBytes("UTF-8"))
      val manager = freshManager
      val tui = TUI(manager, makeReadLine(s"load-json ${tmp.toString}"))
      val out = captureOutput { tui.start() }
      out should include("Loaded from")
    finally
      java.nio.file.Files.deleteIfExists(tmp)
  }

  it should "print JSON error message when JSON file is malformed" in {
    val tmp = java.nio.file.Files.createTempFile("chess-bad", ".json")
    try
      java.nio.file.Files.write(tmp, """{"notfen":"invalid"}""".getBytes("UTF-8"))
      val manager = freshManager
      val tui = TUI(manager, makeReadLine(s"load-json ${tmp.toString}"))
      val out = captureOutput { tui.start() }
      out should include("JSON error")
    finally
      java.nio.file.Files.deleteIfExists(tmp)
  }
