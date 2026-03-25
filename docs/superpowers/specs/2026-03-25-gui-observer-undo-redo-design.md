# GUI + Observer Pattern + Undo/Redo — Design Spec

**Date:** 2026-03-25
**Status:** Approved

---

## 1. Goal

Add a ScalaFX GUI to the existing chess application. Both GUI and TUI share a single game state via an observer pattern. A move made in either UI is immediately reflected in the other. Both UIs support undo and redo.

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                        Main.scala                        │
│  Creates GameManager, starts TUI (daemon thread) +       │
│  ScalaFX GUI (main thread)                               │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│             GameManager  (only mutable component)        │
│  - current:  GameController                              │
│  - history:  List[GameController]   ← undo stack         │
│  - future:   List[GameController]   ← redo stack         │
│  - observers: Buffer[Observer]                           │
│  + move(input: String, caller: Observer): String         │
│  + undo(caller: Observer): String                        │
│  + redo(caller: Observer): String                        │
│  + addObserver(o: Observer): Unit                        │
│  + state: GameController                                 │
└──────────────────────┬───────────────────────────────────┘
                       │  notifyObservers(msg, skip=caller)
              ┌────────┴────────┐
              ▼                 ▼
           TUI               ChessGUI
        (Observer)           (Observer)
```

**Unchanged:** `GameController`, `Board`, all model classes, `CommandParser`, `Renderer`.

---

## 3. New & Modified Files

| File | Action |
|------|--------|
| `controller/Observer.scala` | NEW — observer trait |
| `controller/GameManager.scala` | NEW — mutable observable game state |
| `gui/ChessGUI.scala` | NEW — ScalaFX window + board |
| `gui/ChessApp.scala` | NEW — JavaFX Application entry point |
| `tui/TUI.scala` | MODIFY — takes GameManager, adds undo/redo, implements Observer |
| `Main.scala` | MODIFY — creates GameManager, starts TUI thread + ScalaFX |
| `build.gradle.kts` | MODIFY — add SCALAFX and JAVAFX to root version catalog |
| `core/build.gradle.kts` | MODIFY — add ScalaFX + JavaFX dependencies using version catalog |

---

## 4. Observer Pattern

### `Observer` trait
```scala
trait Observer:
  def onUpdate(ctrl: GameController, message: String): Unit
```

### `GameManager`

The `caller` parameter on `move`, `undo`, and `redo` identifies which observer triggered the action. That observer is **skipped** during notification — preventing double-output when the TUI calls `move()` and `onUpdate` would fire back on the same call stack.

All public methods are `synchronized` to guard against concurrent calls from the TUI thread and the JavaFX Application Thread.

```scala
class GameManager(initial: GameController):
  private var current   = initial
  private var history   = List.empty[GameController]
  private var future    = List.empty[GameController]
  private val observers = mutable.Buffer.empty[Observer]

  def addObserver(o: Observer): Unit = synchronized { observers += o }
  def state: GameController = synchronized { current }

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

  private def notifyObservers(msg: String, skip: Observer | Null): Unit =
    observers.foreach(o => if o ne skip then o.onUpdate(current, msg))
```

**Rules:**
- Invalid moves (same state returned) do not trigger observer notifications.
- Any new `move()` clears the redo stack.
- The `caller` observer is skipped during notification.
- All mutating methods are `synchronized` — safe for concurrent TUI + GUI access.
- History is unbounded (acceptable for a single-session desktop game).

---

## 5. TUI Changes

```scala
class TUI(manager: GameManager, readLine: () => String | Null = () => scala.io.StdIn.readLine())
    extends Observer:

  def start(): Unit =
    manager.addObserver(this)
    loop()

  def onUpdate(ctrl: GameController, msg: String): Unit =
    // Called only when GUI makes a move (TUI is skipped for its own moves)
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
      loop()   // ← at the "if line != null" scope: empty lines skip the move but keep the loop running
```

**Changes from current TUI:**
- Constructor takes `GameManager` instead of `GameController`.
- `readLine` is injected (defaults to `StdIn.readLine`) — allows tests to drive the loop without blocking stdin.
- Implements `Observer` — redraws board with `[GUI]` prefix only when GUI makes a move.
- Recognizes `undo` and `redo` as text commands (case-insensitive).
- Passes `this` as caller to `move`/`undo`/`redo` — prevents self-notification.
- `loop()` call is at the outer `if line != null` scope — empty lines skip the move but continue looping.

---

## 6. GUI Design (ScalaFX)

### Layout
```
┌─────────────────────────────────────┐
│  White's turn                       │  ← status label (top)
├─────────────────────────────────────┤
│  8 │ ♜ │ ♞ │ ♝ │ ♛ │ ♚ │ ♝ │ ♞ │ ♜ │
│  7 │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │ ♟ │
│    │ …                              │
│  1 │ ♖ │ ♘ │ ♗ │ ♕ │ ♔ │ ♗ │ ♘ │ ♖ │
│     a   b   c   d   e   f   g   h   │
├─────────────────────────────────────┤
│  [Undo]  [Redo]   "Moved e2 to e4"  │  ← toolbar (bottom)
└─────────────────────────────────────┘
```

### Move interaction
1. Click a square with a friendly piece → square highlighted yellow.
2. Click destination → `manager.move("e2 e4", this)` called.
3. Invalid move → selection reset, error shown in status label.
4. Click the selected square again → deselect.

### Observer implementation
```scala
// @scoverage.Coverage(excluded=true) — JavaFX lifecycle not testable headless
class ChessGUI(manager: GameManager, stage: Stage) extends Observer:

  def show(): Unit =
    manager.addObserver(this)
    // build and display ScalaFX scene

  def onUpdate(ctrl: GameController, msg: String): Unit =
    Platform.runLater:
      // ctrl is the authoritative state captured at notification time — safe to use directly
      // Do NOT re-read manager.state inside runLater (race condition)
      redrawBoard(ctrl)
      statusLabel.text = msg
```

`Platform.runLater` is required because the TUI thread is not the JavaFX Application Thread.
The `ctrl` parameter is the source of truth for rendering.

### Visual style
- Light squares: `#F0D9B5`, dark squares: `#B58863` (classic wood look)
- Selected square: `#F6F669` (yellow highlight)
- Pieces: Unicode chess symbols, 36px font (same set as TUI Renderer)
- Board size: 480×480px (60px per square)

---

## 7. Threading Model

```scala
@main def main(): Unit =
  System.setOut(PrintStream(System.out, true, "UTF-8"))
  val manager = GameManager(GameController(Board.initial))
  val tui = TUI(manager)

  val tuiThread = Thread(() => tui.start(), "tui-thread")
  tuiThread.setDaemon(true)
  tuiThread.start()

  ChessApp.manager = manager
  Application.launch(classOf[ChessApp])
```

```scala
object ChessApp:
  // @volatile ensures the JavaFX Application Thread sees the write from the main thread
  @volatile var manager: GameManager = _

class ChessApp extends Application:
  override def start(stage: Stage): Unit =
    ChessGUI(ChessApp.manager, stage).show()
```

**Lifecycle:**
- Close GUI → JavaFX exits → main thread ends → daemon TUI thread dies automatically.
- EOF in TUI (Ctrl+D / Ctrl+Z) → TUI thread ends, GUI stays open until window is closed.

---

## 8. Testing Strategy

### `GameManager` (100% coverage target)
- `move()` notifies all registered observers on valid move
- `move()` does not notify on invalid move
- `move()` skips the caller observer
- `undo()` restores previous state and notifies non-caller observers
- `undo()` on empty history returns "Nothing to undo" without notification
- `redo()` after undo restores undone state
- `redo()` on empty future returns "Nothing to redo"
- New `move()` after undo clears redo stack
- Multiple observers all receive notification (except caller)

### `TUI` (extend existing tests)
- `undo` text input calls `manager.undo(tui)` — verified by injecting a `MockGameManager`
- `redo` text input calls `manager.redo(tui)` — verified similarly
- `onUpdate` prints `[GUI]` prefix — driven by direct `tui.onUpdate(ctrl, msg)` call
- Empty line input is ignored (no call to manager), loop continues
- `readLine` injection used to drive the loop without blocking stdin

### `ChessGUI`
- Excluded from scoverage via `@scoverage.Coverage(excluded=true)` on the class.
- Exclusion documented in `docs/unresolved.md`.

### Helper
```scala
class MockObserver extends Observer:
  var updates: List[(GameController, String)] = Nil
  def onUpdate(ctrl: GameController, msg: String): Unit =
    updates = (ctrl, msg) :: updates
```

---

## 9. Build Changes

### Root `build.gradle.kts` — add to the `versions` map
```kotlin
val versions = mapOf(
    // ... existing entries ...
    "SCALAFX"  to "21.0.0-R32",
    "JAVAFX"   to "21"
)
```

### `core/build.gradle.kts` — dependencies and run task

```kotlin
// ScalaFX wrapper
implementation("org.scalafx:scalafx_3:${versions["SCALAFX"]!!}")

// JavaFX modules with native classifier.
// "win" targets Windows. For macOS use "mac", for Linux use "linux".
// Cross-platform builds require the JavaFX Gradle Plugin.
listOf("javafx-base", "javafx-controls", "javafx-graphics").forEach { module ->
  implementation("org.openjfx:$module:${versions["JAVAFX"]!!}:win")
}
```

Merge into the **existing** `tasks.named<JavaExec>("run")` block (do not replace it):
```kotlin
tasks.named<JavaExec>("run") {
  jvmArgs(
    "-Dfile.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
    "--add-modules=javafx.controls,javafx.graphics"   // single = form required
  )
  standardInput = System.`in`
}
```

**Notes:**
- `javafx-fxml` is intentionally excluded — the GUI is built programmatically, not with FXML.
- `--add-modules` must be a single `=`-delimited string, not two separate arguments.
- The existing `standardInput = System.in` and encoding args are preserved.

---

## 10. Constraints & Non-Goals

- No promotion dialog (pawn promotion not implemented in Board)
- No drag-and-drop (click-click interaction only)
- No TestFX integration tests (out of scope)
- No persistence (save/load game)
- No `removeObserver` — TUI runs as daemon thread and dies with the JVM; no cleanup needed
