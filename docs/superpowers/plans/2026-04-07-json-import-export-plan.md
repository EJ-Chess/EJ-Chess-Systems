# JSON Import/Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement JSON import/export of game positions (FEN + metadata) with integration into TUI (`save-json`/`load-json` commands) and GUI (buttons).

**Architecture:** Hand-crafted JSON parser (no external libraries) following the FEN/PGN pattern. `Json.scala` object with `encode` and `decode`. TUI extends command parsing in the main loop. GUI adds two file-chooser buttons mirroring PGN buttons.

**Tech Stack:** Scala 3, JavaFX (GUI only), java.time.LocalDate, java.io for file I/O

---

## File Structure

| File | Action | Purpose |
|---|---|---|
| `core/src/main/scala/de/eljachess/chess/model/Json.scala` | Create | Encode/decode JSON with FEN |
| `core/src/test/scala/de/eljachess/chess/model/JsonSpec.scala` | Create | Unit tests for Json object |
| `core/src/main/scala/de/eljachess/chess/tui/TUI.scala` | Modify | Add `save-json` / `load-json` command parsing |
| `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` | Modify | Add Export JSON / Import JSON buttons |

---

## Task 1: Create Json object (encode + decode)

**Files:**
- Create: `core/src/main/scala/de/eljachess/chess/model/Json.scala`
- Create: `core/src/test/scala/de/eljachess/chess/model/JsonSpec.scala`

- [ ] **Step 1: Write failing tests for Json.encode**

Create `core/src/test/scala/de/eljachess/chess/model/JsonSpec.scala`:

```scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonSpec extends AnyFlatSpec with Matchers:

  "Json.encode" should "produce valid JSON with fen field" in {
    val ctrl = GameController(Board.initial)
    val json = Json.encode(ctrl, "White", "Black")
    json should include("\"fen\"")
    json should include("\"whiteName\"")
    json should include("\"blackName\"")
    json should include("\"date\"")
  }

  it should "include initial FEN in output" in {
    val ctrl = GameController(Board.initial)
    val json = Json.encode(ctrl)
    json should include("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
  }

  it should "use default player names if not provided" in {
    val ctrl = GameController(Board.initial)
    val json = Json.encode(ctrl)
    json should include("\"whiteName\":\"White\"")
    json should include("\"blackName\":\"Black\"")
  }

  "Json.decode" should "parse valid JSON and return GameController" in {
    val json = """{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","whiteName":"A","blackName":"B","date":"2026-04-07"}"""
    Json.decode(json) match
      case Right(ctrl) => ctrl.board.pieceAt(Square(0, 0)) shouldBe Some(Piece(Color.Black, PieceKind.Rook))
      case Left(err)   => fail(err)
  }

  it should "return Left when fen field is missing" in {
    val json = """{"whiteName":"A","blackName":"B","date":"2026-04-07"}"""
    Json.decode(json) match
      case Left(err) => err should include("fen")
      case Right(_)  => fail("Should have failed")
  }

  it should "return Left when fen is invalid" in {
    val json = """{"fen":"invalid fen","whiteName":"A","blackName":"B","date":"2026-04-07"}"""
    Json.decode(json) match
      case Left(err) => succeed
      case Right(_)  => fail("Should have failed")
  }

  it should "ignore metadata fields (whiteName, blackName, date) on decode" in {
    val json = """{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","whiteName":"Ignored","blackName":"Ignored","date":"2000-01-01"}"""
    Json.decode(json) match
      case Right(ctrl) => ctrl shouldBe GameController(Board.initial)
      case Left(err)   => fail(err)
  }

  it should "handle whitespace and newlines in JSON" in {
    val json = """{
      "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      "whiteName": "A",
      "blackName": "B",
      "date": "2026-04-07"
    }"""
    Json.decode(json) match
      case Right(ctrl) => ctrl.board.pieceAt(Square(0, 0)) shouldBe Some(Piece(Color.Black, PieceKind.Rook))
      case Left(err)   => fail(err)
  }
```

Run: `./gradlew :core:test --tests "*JsonSpec*" -v`  
Expected: All 7 tests FAIL (Json object does not exist yet)

- [ ] **Step 2: Implement Json.encode**

Create `core/src/main/scala/de/eljachess/chess/model/Json.scala`:

```scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController
import java.time.LocalDate

object Json:

  def encode(ctrl: GameController, whiteName: String = "White", blackName: String = "Black"): String =
    val fen  = Fen.encode(ctrl)
    val date = LocalDate.now().toString
    s"""{
  "fen": "$fen",
  "whiteName": "$whiteName",
  "blackName": "$blackName",
  "date": "$date"
}"""

  val decode: String => Either[String, GameController] = jsonStr =>
    val fenPattern = """"fen"\s*:\s*"([^"]*)"""".r
    fenPattern.findFirstMatchIn(jsonStr) match
      case None => Left("Invalid JSON: missing field 'fen'")
      case Some(m) =>
        val fenValue = m.group(1)
        Fen.decode(fenValue)
```

Run: `./gradlew :core:test --tests "*JsonSpec*" -v`  
Expected: All 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/model/Json.scala core/src/test/scala/de/eljachess/chess/model/JsonSpec.scala
git commit -m "feat(json): add encode/decode for game position (FEN + metadata)"
```

---

## Task 2: Integrate JSON into TUI (`save-json` / `load-json` commands)

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/tui/TUI.scala`
- Modify: `core/src/test/scala/de/eljachess/chess/tui/TUISpec.scala` (add tests)

- [ ] **Step 1: Write failing tests for TUI JSON commands**

Add to `core/src/test/scala/de/eljachess/chess/tui/TUISpec.scala`:

```scala
  "TUI loop" should "save game to JSON with save-json <filename> command" in {
    val manager = freshManager
    manager.move("e2 e4")  // make a move
    val tui = TUI(manager, makeReadLine("save-json test-game.json"))
    captureOutput { tui.start() }
    java.nio.file.Files.exists(java.nio.file.Paths.get("test-game.json")) shouldBe true
    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("test-game.json"))
  }

  it should "load game from JSON with load-json <filename> command" in {
    // Create a JSON file first
    val json = Json.encode(GameController(Board.initial), "Test", "Player")
    java.nio.file.Files.write(
      java.nio.file.Paths.get("test-load.json"),
      json.getBytes("UTF-8")
    )
    val manager = freshManager
    manager.move("e2 e4")  // change state
    val tui = TUI(manager, makeReadLine("load-json test-load.json"))
    captureOutput { tui.start() }
    manager.state.board shouldBe Board.initial
    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("test-load.json"))
  }

  it should "print error on load-json with missing file" in {
    val manager = freshManager
    val tui = TUI(manager, makeReadLine("load-json nonexistent.json"))
    val out = captureOutput { tui.start() }
    out should include("Error") or include("not found")
  }
```

Run: `./gradlew :core:test --tests "*TUISpec*" -v`  
Expected: 3 new tests FAIL

- [ ] **Step 2: Modify TUI to handle save-json and load-json**

In `core/src/main/scala/de/eljachess/chess/tui/TUI.scala`, modify the `loop()` method. Find the section:

```scala
@tailrec
private def loop(): Unit =
  val ctrl = manager.state
  println(Renderer.render(ctrl.board, ctrl.currentTurn))
  val line = readLine()
  if line != null then
    val trimmed = line.trim
    if trimmed.nonEmpty then
      val msg = trimmed.toLowerCase match
        case "undo" => manager.undo(this)
        case "redo" => manager.redo(this)
        case _      => manager.move(trimmed, this)
      println(msg)
    loop()
```

Replace with:

```scala
@tailrec
private def loop(): Unit =
  val ctrl = manager.state
  println(Renderer.render(ctrl.board, ctrl.currentTurn))
  val line = readLine()
  if line != null then
    val trimmed = line.trim
    if trimmed.nonEmpty then
      val msg = trimmed.toLowerCase match
        case "undo" => manager.undo(this)
        case "redo" => manager.redo(this)
        case s if s.startsWith("save-json ") =>
          val filename = trimmed.stripPrefix("save-json ").trim
          try
            val json = Json.encode(manager.state, "White", "Black")
            java.nio.file.Files.write(
              java.nio.file.Paths.get(filename),
              json.getBytes("UTF-8")
            )
            s"Saved to $filename"
          catch
            case e: Exception => s"Error: ${e.getMessage}"
        case s if s.startsWith("load-json ") =>
          val filename = trimmed.stripPrefix("load-json ").trim
          try
            val json = java.nio.file.Files.readString(java.nio.file.Paths.get(filename), "UTF-8")
            Json.decode(json) match
              case Left(err) => s"JSON error: $err"
              case Right(ctrl) =>
                manager.move(s"load ${Fen.encode(ctrl)}", this)
          catch
            case e: Exception => s"Error: ${e.getMessage}"
        case _ => manager.move(trimmed, this)
      println(msg)
    loop()
```

Add import at the top:
```scala
import de.eljachess.chess.model.{Fen, Json}
```

Run: `./gradlew :core:test --tests "*TUISpec*" -v`  
Expected: All 3 new tests PASS (plus all existing tests still pass)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/tui/TUI.scala core/src/test/scala/de/eljachess/chess/tui/TUISpec.scala
git commit -m "feat(tui): add save-json and load-json commands"
```

---

## Task 3: Add JSON buttons to GUI

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala`

- [ ] **Step 1: Add Export JSON button method**

In `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala`, find the `buildScene()` method where buttons are created (around line 66-81). After `val exportPgnBtn = buildExportPgnButton()` add:

```scala
    val exportJsonBtn = buildExportJsonButton()
```

Then in the grid add statements at line 81, modify to:
```scala
    btnGrid.add(undoBtn,       0, 0); btnGrid.add(redoBtn,       1, 0)
    btnGrid.add(copyFenBtn,    0, 1); btnGrid.add(loadFenBtn,    1, 1)
    btnGrid.add(importPgnBtn,  0, 2); btnGrid.add(exportPgnBtn,  1, 2)
    btnGrid.add(importJsonBtn, 0, 3); btnGrid.add(exportJsonBtn, 1, 3)
```

Add at the top where other buttons are declared:
```scala
    val importJsonBtn = buildImportJsonButton(manager)
```

Now add the two button builder methods at the end of the class (before the last closing brace). Add after `buildImportPgnButton`:

```scala
  private def buildExportJsonButton(): Button =
    val btn = Button("Export JSON")
    btn.setOnAction { _ =>
      val dialog = new TextInputDialog()
      dialog.setTitle("Spieler eingeben")
      dialog.setHeaderText("Spielernamen für JSON")
      dialog.setContentText("Weiß, Schwarz (kommagetrennt):")
      dialog.showAndWait().toScala match
        case Some(input) if input.nonEmpty =>
          val names = input.split(",").map(_.trim)
          if names.length == 2 then
            try
              val json = Json.encode(manager.state, names(0), names(1))
              val chooser = new FileChooser()
              chooser.setTitle("Export JSON File")
              chooser.getExtensionFilters.add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json")
              )
              val file = chooser.showSaveDialog(stage)
              if file != null then
                java.nio.file.Files.write(
                  java.nio.file.Paths.get(file.getAbsolutePath),
                  json.getBytes("UTF-8")
                )
                msgLabel.setText("JSON exported")
            catch
              case e: Exception => msgLabel.setText(s"JSON error: ${e.getMessage}")
          else
            msgLabel.setText("Format: White, Black")
        case _ => ()
    }
    btn

  // $COVERAGE-OFF$
  private def buildImportJsonButton(manager: GameManager): Button =
    val button = new Button("Import JSON")
    button.setOnAction { _ =>
      val chooser = new FileChooser()
      chooser.setTitle("Import JSON File")
      chooser.getExtensionFilters.add(
        new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json")
      )
      val file = chooser.showOpenDialog(stage)
      if file != null then
        try
          val content = java.nio.file.Files.readString(
            java.nio.file.Paths.get(file.getAbsolutePath),
            "UTF-8"
          )
          Json.decode(content) match
            case Left(err) => msgLabel.setText(s"JSON error: $err")
            case Right(ctrl) =>
              val msg = manager.move(s"load ${Fen.encode(ctrl)}", this)
              selected = None
              currentCtrl = manager.state
              redrawBoard(currentCtrl)
              msgLabel.setText(msg)
        catch
          case e: java.io.IOException => msgLabel.setText(s"JSON error: file not found")
          case e: Exception => msgLabel.setText(s"JSON error: ${e.getMessage}")
    }
    button
  // $COVERAGE-ON$
```

Add import at the top:
```scala
import de.eljachess.chess.model.{Color as ChessColor, Pgn, Piece, PieceKind, Square, Fen, Json}
```

- [ ] **Step 2: Run tests to ensure nothing broke**

```bash
./gradlew :core:test -v
```

Expected: All existing tests still pass (JSON button tests are excluded from coverage)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala
git commit -m "feat(gui): add Export JSON and Import JSON buttons"
```

---

## Summary Checklist

- [ ] Json.scala created with encode/decode
- [ ] JsonSpec.scala with full test coverage
- [ ] TUI supports `save-json` and `load-json` commands
- [ ] TUI tests pass (file I/O, error cases)
- [ ] GUI has Export JSON button (prompts for player names, file chooser, file write)
- [ ] GUI has Import JSON button (file chooser, file read, decode, load position)
- [ ] All tests pass: `./gradlew :core:test -v`
- [ ] All commits created and pushed
