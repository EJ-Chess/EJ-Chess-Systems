# GUI + Observer Pattern + Undo/Redo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a ScalaFX GUI connected to the existing TUI via an observer pattern, with shared undo/redo support in both UIs.

**Architecture:** A new `GameManager` class holds the mutable game state (current `GameController` + undo/redo stacks) and notifies registered `Observer`s on every state change. The TUI and GUI both implement `Observer` and register with the manager. Each caller passes `this` when calling `move`/`undo`/`redo` so the manager skips notifying the originator, preventing double-rendering.

**Tech Stack:** Scala 3.5.1, ScalaFX 21.0.0-R32, JavaFX 21 (Windows native classifier), Gradle, ScalaTest 3.2.19

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `build.gradle.kts` | MODIFY | Add `SCALAFX` + `JAVAFX` to root version catalog |
| `core/build.gradle.kts` | MODIFY | Add ScalaFX/JavaFX deps, update run task JVM args |
| `core/src/main/scala/de/eljachess/chess/controller/Observer.scala` | CREATE | `Observer` trait |
| `core/src/main/scala/de/eljachess/chess/controller/GameManager.scala` | CREATE | Mutable observable: state + undo/redo stacks + observer notification |
| `core/src/test/scala/de/eljachess/chess/controller/GameManagerSpec.scala` | CREATE | Unit tests for GameManager |
| `core/src/main/scala/de/eljachess/chess/tui/TUI.scala` | MODIFY | Takes `GameManager`, implements `Observer`, adds undo/redo text commands |
| `core/src/test/scala/de/eljachess/chess/tui/TUISpec.scala` | CREATE | Unit tests for refactored TUI |
| `core/src/main/scala/de/eljachess/chess/gui/ChessApp.scala` | CREATE | JavaFX `Application` entry point; holds `@volatile manager` reference |
| `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` | CREATE | ScalaFX/JavaFX board window; implements `Observer` |
| `core/src/main/scala/de/eljachess/chess/Main.scala` | MODIFY | Creates `GameManager`, starts TUI daemon thread, launches JavaFX |
| `docs/unresolved.md` | MODIFY | Document ChessGUI scoverage exclusion |

---

## Task 1: Build Configuration

**Files:**
- Modify: `build.gradle.kts`
- Modify: `core/build.gradle.kts`

- [ ] **Step 1: Add version entries to root build file**

Open `build.gradle.kts` and add `SCALAFX` and `JAVAFX` to the versions map:

```kotlin
val versions = mapOf(
    "QUARKUS_SCALA3"        to "1.0.0",
    "SCALA3"                to "3.5.1",
    "SCALA_LIBRARY"         to "2.13.18",
    "SCALATEST"             to "3.2.19",
    "SCALATEST_JUNIT"       to "0.1.11",
    "SCOVERAGE"             to "2.1.1",
    "SCALAFX"               to "21.0.0-R32",
    "JAVAFX"                to "21"
)
extra["VERSIONS"] = versions
```

- [ ] **Step 2: Add ScalaFX/JavaFX dependencies to core/build.gradle.kts**

In the `dependencies { }` block of `core/build.gradle.kts`, add after the existing `implementation` lines:

```kotlin
implementation("org.scalafx:scalafx_3:${versions["SCALAFX"]!!}")
listOf("javafx-base", "javafx-controls", "javafx-graphics").forEach { module ->
    implementation("org.openjfx:$module:${versions["JAVAFX"]!!}:win")
}
```

- [ ] **Step 3: Update the run task JVM args in core/build.gradle.kts**

Replace the existing `tasks.named<JavaExec>("run")` block with the merged version:

```kotlin
tasks.named<JavaExec>("run") {
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "--add-modules=javafx.controls,javafx.graphics"
    )
    standardInput = System.`in`
}
```

- [ ] **Step 4: Verify the build resolves dependencies**

```
./gradlew :core:dependencies --configuration runtimeClasspath 2>&1 | grep -i scalafx
```

Expected: `org.scalafx:scalafx_3:21.0.0-R32` appears in the output.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts core/build.gradle.kts
git commit -m "build: add ScalaFX and JavaFX 21 dependencies"
```

---

## Task 2: Observer Trait + GameManager (TDD)

**Files:**
- Create: `core/src/main/scala/de/eljachess/chess/controller/Observer.scala`
- Create: `core/src/test/scala/de/eljachess/chess/controller/GameManagerSpec.scala`
- Create: `core/src/main/scala/de/eljachess/chess/controller/GameManager.scala`

- [ ] **Step 1: Create Observer trait**

Create `core/src/main/scala/de/eljachess/chess/controller/Observer.scala`:

```scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.Board

trait Observer:
  def onUpdate(ctrl: GameController, message: String): Unit
```

- [ ] **Step 2: Write failing tests for GameManager**

Create `core/src/test/scala/de/eljachess/chess/controller/GameManagerSpec.scala`:

```scala
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
```

- [ ] **Step 3: Run tests — expect compilation failure (GameManager doesn't exist yet)**

```
./gradlew :core:test 2>&1 | tail -20
```

Expected: compilation error referencing `GameManager`.

- [ ] **Step 4: Implement GameManager**

Create `core/src/main/scala/de/eljachess/chess/controller/GameManager.scala`:

```scala
package de.eljachess.chess.controller

import scala.collection.mutable

class GameManager(initial: GameController):
  private var current   = initial
  private var history   = List.empty[GameController]
  private var future    = List.empty[GameController]
  private val observers = mutable.Buffer.empty[Observer]

  def addObserver(o: Observer): Unit = synchronized { observers += o }
  def state: GameController           = synchronized { current }

  def move(input: String, caller: Observer | Null = null): String = synchronized {
    val (next, msg) = current.handleCommand(input)
    if next != current then
      history = current :: history
      future  = Nil
      current = next
      notifyObservers(msg, skip = caller)
    msg
  }

  def undo(caller: Observer | Null = null): String = synchronized {
    history match
      case Nil          => "Nothing to undo"
      case prev :: rest =>
        future  = current :: future
        history = rest
        current = prev
        notifyObservers("Undo", skip = caller)
        "Undo"
  }

  def redo(caller: Observer | Null = null): String = synchronized {
    future match
      case Nil          => "Nothing to redo"
      case next :: rest =>
        history = current :: history
        future  = rest
        current = next
        notifyObservers("Redo", skip = caller)
        "Redo"
  }

  // Called only from within synchronized methods — do NOT call manager methods from observer.onUpdate
  private def notifyObservers(msg: String, skip: Observer | Null): Unit =
    observers.foreach(o => if o ne skip then o.onUpdate(current, msg))
```

- [ ] **Step 5: Run tests — expect green**

```
./gradlew :core:test 2>&1 | tail -20
```

Expected: All `GameManagerSpec` tests PASSED.

- [ ] **Step 6: Check coverage**

```
python jacoco-reporter/scoverage_coverage_gaps.py core/build/reports/scoverageTest/scoverage.xml 2>&1 | grep -A3 "GameManager"
```

Expected: statement-rate and branch-rate both 100.00 for GameManager.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/controller/Observer.scala \
        core/src/main/scala/de/eljachess/chess/controller/GameManager.scala \
        core/src/test/scala/de/eljachess/chess/controller/GameManagerSpec.scala
git commit -m "feat: add Observer trait and GameManager with undo/redo"
```

---

## Task 3: TUI Refactor (TDD)

**Files:**
- Create: `core/src/test/scala/de/eljachess/chess/tui/TUISpec.scala`
- Modify: `core/src/main/scala/de/eljachess/chess/tui/TUI.scala`

- [ ] **Step 1: Write failing tests for refactored TUI**

Create `core/src/test/scala/de/eljachess/chess/tui/TUISpec.scala`:

```scala
package de.eljachess.chess.tui

import de.eljachess.chess.controller.{GameController, GameManager, Observer}
import de.eljachess.chess.model.{Board, Color, Piece, PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.{ByteArrayOutputStream, PrintStream}

class TUISpec extends AnyFlatSpec with Matchers:

  /** Captures stdout produced by f, then restores the original stream. */
  private def captureOutput(f: => Unit): String =
    val buf = ByteArrayOutputStream()
    val ps  = PrintStream(buf, true, "UTF-8")
    val orig = System.out
    System.setOut(ps)
    try f finally System.setOut(orig)
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
```

- [ ] **Step 2: Run tests — expect compilation failure (TUI signature changed)**

```
./gradlew :core:test 2>&1 | tail -20
```

Expected: Compilation error because current TUI takes `GameController`, not `GameManager`.

- [ ] **Step 3: Rewrite TUI.scala**

Replace the entire content of `core/src/main/scala/de/eljachess/chess/tui/TUI.scala`:

```scala
package de.eljachess.chess.tui

import de.eljachess.chess.controller.{GameController, GameManager, Observer}
import scala.annotation.tailrec

class TUI(manager: GameManager, readLine: () => String | Null = () => scala.io.StdIn.readLine())
    extends Observer:

  def start(): Unit =
    manager.addObserver(this)
    loop()

  def onUpdate(ctrl: GameController, msg: String): Unit =
    // Invoked only for moves originating from another observer (e.g. the GUI)
    println(s"\n[GUI] $msg")
    println(Renderer.render(ctrl.board, ctrl.currentTurn))

  @tailrec
  private def loop(): Unit =
    println(Renderer.render(manager.state.board, manager.state.currentTurn))
    val line = readLine()
    if line != null then
      val trimmed = line.trim
      if trimmed.nonEmpty then
        val msg = trimmed.toLowerCase match
          case "undo" => manager.undo(this)
          case "redo" => manager.redo(this)
          case _      => manager.move(trimmed, this)
        println(msg)
      loop() // always recurse while input is non-null; empty lines skip the move
```

- [ ] **Step 4: Run all tests — expect green**

```
./gradlew :core:test 2>&1 | tail -30
```

Expected: All `TUISpec` and `GameManagerSpec` tests PASSED. All previously-passing tests still PASSED.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/tui/TUI.scala \
        core/src/test/scala/de/eljachess/chess/tui/TUISpec.scala
git commit -m "feat: refactor TUI to use GameManager, add undo/redo commands"
```

---

## Task 4: Update Main.scala (TUI thread only, no GUI yet)

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/Main.scala`

- [ ] **Step 1: Update Main.scala to use GameManager and TUI thread**

Replace the entire content of `core/src/main/scala/de/eljachess/chess/Main.scala`:

```scala
package de.eljachess.chess

import de.eljachess.chess.controller.{GameController, GameManager}
import de.eljachess.chess.model.Board
import de.eljachess.chess.tui.TUI
import java.io.PrintStream

@main def main(): Unit =
  System.setOut(PrintStream(System.out, true, "UTF-8"))
  val manager = GameManager(GameController(Board.initial))
  TUI(manager).start()
```

- [ ] **Step 2: Build and run to verify TUI still works**

```
./gradlew :core:run --console=plain
```

Expected: Chess board is displayed. `e2 e4` makes a move. `undo` reverts it. `redo` reapplies it. Ctrl+D exits.

- [ ] **Step 3: Run tests to confirm nothing broke**

```
./gradlew :core:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/Main.scala
git commit -m "feat: wire Main to GameManager"
```

---

## Task 5: ScalaFX GUI — ChessApp + ChessGUI

**Files:**
- Create: `core/src/main/scala/de/eljachess/chess/gui/ChessApp.scala`
- Create: `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala`
- Modify: `core/build.gradle.kts` (scoverage exclusion)
- Modify: `docs/unresolved.md`

- [ ] **Step 1: Create ChessApp.scala (JavaFX Application entry point)**

Create `core/src/main/scala/de/eljachess/chess/gui/ChessApp.scala`:

```scala
package de.eljachess.chess.gui

import de.eljachess.chess.controller.GameManager
import javafx.application.Application
import javafx.stage.Stage

object ChessApp:
  // Written by the main thread before Application.launch — @volatile ensures
  // the JavaFX Application Thread sees the assignment without a data race.
  @volatile var manager: GameManager = _

class ChessApp extends Application:
  override def start(stage: Stage): Unit =
    ChessGUI(ChessApp.manager, stage).show()
```

- [ ] **Step 2: Create ChessGUI.scala**

Create `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala`:

```scala
package de.eljachess.chess.gui

import de.eljachess.chess.controller.{GameController, GameManager, Observer}
import de.eljachess.chess.model.{Color as ChessColor, Piece, PieceKind, Square}
import javafx.application.Platform
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.{Button, Label}
import javafx.scene.layout.{BorderPane, GridPane, HBox, StackPane}
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.{Font, Text}
import javafx.stage.Stage

// Excluded from scoverage — JavaFX lifecycle cannot be tested headless.
// See docs/unresolved.md for details.
class ChessGUI(manager: GameManager, stage: Stage) extends Observer:

  private val squareSize  = 60
  private val grid        = GridPane()
  private val statusLabel = Label("White's turn")
  private val msgLabel    = Label("")
  private var selected: Option[Square] = None
  private var currentCtrl = manager.state

  def show(): Unit =
    manager.addObserver(this)
    buildScene()
    redrawBoard(currentCtrl)
    stage.show()

  // Called from TUI thread — must dispatch to JavaFX thread via Platform.runLater.
  // Use the `ctrl` parameter as source of truth; do NOT re-read manager.state here.
  def onUpdate(ctrl: GameController, msg: String): Unit =
    Platform.runLater: () =>
      selected = None
      redrawBoard(ctrl)
      msgLabel.setText(msg)

  private def buildScene(): Unit =
    val root = BorderPane()

    statusLabel.setFont(Font.font(16))
    statusLabel.setPadding(Insets(8))
    root.setTop(statusLabel)

    root.setCenter(grid)

    val undoBtn = Button("Undo")
    val redoBtn = Button("Redo")
    undoBtn.setOnAction(_ => doAction(manager.undo(this)))
    redoBtn.setOnAction(_ => doAction(manager.redo(this)))
    msgLabel.setPadding(Insets(0, 8, 0, 8))
    val toolbar = HBox(8.0, undoBtn, redoBtn, msgLabel)
    toolbar.setPadding(Insets(8))
    toolbar.setAlignment(Pos.CENTER_LEFT)
    root.setBottom(toolbar)

    stage.setTitle("ElJa Chess")
    stage.setScene(Scene(root, (squareSize * 8).toDouble, (squareSize * 8 + 80).toDouble))
    stage.setResizable(false)

  // Called after GUI-originated undo/redo (caller = this, so onUpdate was skipped).
  private def doAction(msg: String): Unit =
    selected = None
    currentCtrl = manager.state
    redrawBoard(currentCtrl)
    msgLabel.setText(msg)

  private def redrawBoard(ctrl: GameController): Unit =
    currentCtrl = ctrl
    grid.getChildren.clear()
    statusLabel.setText(if ctrl.currentTurn == ChessColor.White then "White's turn" else "Black's turn")
    for row <- 7 to 0 by -1 do
      for col <- 0 to 7 do
        val sq = Square(col, row)
        val isLight    = (col + row) % 2 == 1
        val isSelected = selected.contains(sq)
        val bg = if isSelected then Color.web("#F6F669")
                 else if isLight then Color.web("#F0D9B5")
                 else Color.web("#B58863")
        val rect  = Rectangle(squareSize.toDouble, squareSize.toDouble, bg)
        val label = Text(ctrl.board.pieceAt(sq).map(pieceSymbol).getOrElse(""))
        label.setFont(Font.font(36))
        val cell = StackPane(rect, label)
        cell.setOnMouseClicked(_ => handleClick(sq))
        grid.add(cell, col, 7 - row)

  private def handleClick(sq: Square): Unit =
    selected match
      case None =>
        currentCtrl.board.pieceAt(sq) match
          case Some(p) if p.color == currentCtrl.currentTurn =>
            selected = Some(sq)
            redrawBoard(currentCtrl)
          case _ => ()
      case Some(from) if from == sq =>
        selected = None
        redrawBoard(currentCtrl)
      case Some(from) =>
        val move = s"${from.toAlgebraic} ${sq.toAlgebraic}"
        val msg  = manager.move(move, this)   // TUI is notified; GUI is not (caller = this)
        selected     = None
        currentCtrl  = manager.state          // read updated state directly
        redrawBoard(currentCtrl)
        msgLabel.setText(msg)

  private def pieceSymbol(piece: Piece): String = piece match
    case Piece(ChessColor.White, PieceKind.King)   => "♔"
    case Piece(ChessColor.White, PieceKind.Queen)  => "♕"
    case Piece(ChessColor.White, PieceKind.Rook)   => "♖"
    case Piece(ChessColor.White, PieceKind.Bishop) => "♗"
    case Piece(ChessColor.White, PieceKind.Knight) => "♘"
    case Piece(ChessColor.White, PieceKind.Pawn)   => "♙"
    case Piece(ChessColor.Black, PieceKind.King)   => "♚"
    case Piece(ChessColor.Black, PieceKind.Queen)  => "♛"
    case Piece(ChessColor.Black, PieceKind.Rook)   => "♜"
    case Piece(ChessColor.Black, PieceKind.Bishop) => "♝"
    case Piece(ChessColor.Black, PieceKind.Knight) => "♞"
    case Piece(ChessColor.Black, PieceKind.Pawn)   => "♟"
```

- [ ] **Step 3: Exclude GUI files from scoverage**

In `core/build.gradle.kts`, update the `scoverage` block:

```kotlin
scoverage {
    scoverageVersion.set(versions["SCOVERAGE"]!!)
    excludedFiles.addAll(".*ChessGUI.*", ".*ChessApp.*")
}
```

- [ ] **Step 4: Document exclusion in unresolved.md**

Add to `docs/unresolved.md` (create if it doesn't exist):

```markdown
## [2026-03-25] ChessGUI + ChessApp excluded from scoverage

**Requirement / Bug:**
ChessGUI and ChessApp cannot be covered by automated tests because the JavaFX
lifecycle (Application.start, Stage, Platform.runLater) requires a running
display server and JavaFX Application Thread that are not available in headless
CI/test environments.

**Root Cause (if known):**
JavaFX requires a native window system. Headless testing with TestFX was deemed
out of scope for this feature.

**Attempted Fixes:**
1. Scoverage exclusion configured in core/build.gradle.kts via excludedFiles.

**Suggested Next Step:**
Add TestFX as a test dependency and write a headless smoke test that launches
the application with a mock GameManager to verify board rendering and button callbacks.
```

- [ ] **Step 5: Build to verify compilation**

```
./gradlew :core:build 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL (no compilation errors).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/gui/ \
        core/build.gradle.kts \
        docs/unresolved.md
git commit -m "feat: add ScalaFX GUI with observer pattern"
```

---

## Task 6: Wire GUI into Main + Integration Run

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/Main.scala`

- [ ] **Step 1: Update Main.scala to launch both TUI thread and ScalaFX GUI**

Replace the entire content of `core/src/main/scala/de/eljachess/chess/Main.scala`:

```scala
package de.eljachess.chess

import de.eljachess.chess.controller.{GameController, GameManager}
import de.eljachess.chess.gui.ChessApp
import de.eljachess.chess.model.Board
import de.eljachess.chess.tui.TUI
import java.io.PrintStream
import javafx.application.Application

@main def main(): Unit =
  System.setOut(PrintStream(System.out, true, "UTF-8"))
  val manager = GameManager(GameController(Board.initial))
  val tui     = TUI(manager)

  // TUI runs as a daemon thread: it dies automatically when the GUI closes.
  val tuiThread = Thread(() => tui.start(), "tui-thread")
  tuiThread.setDaemon(true)
  tuiThread.start()

  // The manager reference must be set BEFORE Application.launch is called.
  // @volatile on ChessApp.manager ensures the JavaFX thread sees the write.
  ChessApp.manager = manager
  Application.launch(classOf[ChessApp])
```

- [ ] **Step 2: Run all tests to confirm nothing regressed**

```
./gradlew :core:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Launch the application and perform integration smoke test**

```
./gradlew :core:run --console=plain
```

Manually verify the following:
1. GUI window opens showing the initial chess board.
2. TUI prints the board in the terminal.
3. Type `e2 e4` in the terminal → pawn moves in both TUI and GUI simultaneously; terminal shows `[GUI]` is NOT printed (TUI made the move).
4. Click a white pawn in the GUI → it highlights yellow. Click a valid target square → pawn moves. Terminal shows `[GUI] Moved ...` and redraws the board.
5. Click **Undo** in GUI → last move is reversed in both GUI and TUI (terminal shows `[GUI] Undo`).
6. Type `undo` in terminal → move is reversed in both GUI and TUI.
7. Click **Redo** → move is reapplied in both UIs.
8. Close GUI window → terminal and application exit cleanly.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/Main.scala
git commit -m "feat: launch TUI + GUI together via GameManager observer pattern"
```

---

## Completion Checklist

Before considering this done, confirm:

- [ ] `./gradlew :core:build` is green
- [ ] All `GameManagerSpec` tests pass (100% coverage)
- [ ] All `TUISpec` tests pass
- [ ] All pre-existing tests still pass (`BoardSpec`, `RendererSpec`, `CommandParserSpec`, `GameControllerSpec`)
- [ ] GUI window opens on `./gradlew :core:run`
- [ ] Move in TUI → reflected in GUI
- [ ] Move in GUI → reflected in TUI (`[GUI]` prefix in terminal)
- [ ] `undo`/`redo` work from both terminal and GUI buttons
- [ ] GUI close exits the whole application
- [ ] ChessGUI + ChessApp excluded from scoverage coverage report
- [ ] `docs/unresolved.md` entry written for GUI coverage exclusion
