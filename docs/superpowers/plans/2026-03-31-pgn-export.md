# PGN Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PGN export with SAN notation, GameManager move tracking, and GUI export button.

**Architecture:** A pure `Pgn` object encodes game history to PGN with SAN move notation. `GameManager` extends history tracking to store `(GameController, ParsedMove)` pairs, enabling move reconstruction. GUI gains an "Export PGN" button that prompts for player names and copies to clipboard.

**Tech Stack:** Scala 3, JavaFX (`TextInputDialog`, `Clipboard`), ScalaTest (`AnyFlatSpec with Matchers`), Gradle + scoverage

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `core/src/main/scala/de/eljachess/chess/model/Pgn.scala` | Pure `encode` + `sanForMove` + helper methods |
| **Create** | `core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala` | All PGN/SAN codec tests |
| **Modify** | `core/src/main/scala/de/eljachess/chess/controller/GameManager.scala` | Extend history to `List[(GameController, ParsedMove)]`, add `pgn()` method |
| **Modify** | `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` | "Export PGN" button (scoverage-excluded) |

---

### Task 1: `Pgn.encode` Implementation

**Files:**
- Create: `core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala`
- Create: `core/src/main/scala/de/eljachess/chess/model/Pgn.scala`

- [ ] **Step 1: Write failing PGN header tests**

Create `core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala`:

```scala
// core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PgnSpec extends AnyFlatSpec with Matchers:

  "Pgn.encode" should "include 7-tag header with provided player names" in {
    val headers = Pgn.encode(List(), "Alice", "Bob", GameController(Board.initial))
    headers should include("[White \"Alice\"]")
    headers should include("[Black \"Bob\"]")
    headers should include("[Event \"?\"]")
    headers should include("[Site \"?\"]")
    headers should include("[Round \"?\"]")
  }

  it should "include today's date in YYYY.MM.DD format" in {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    val headers = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    headers should include(s"[Date \"$today\"]")
  }

  it should "detect in-progress game as result *" in {
    val headers = Pgn.encode(List(), "White", "Black", GameController(Board.initial))
    headers should include("[Result \"*\"]")
  }

  it should "detect checkmate as 1-0 when Black to move and checkmated" is (pending) // Requires board construction with checkmate position

  it should "detect stalemate as 1/2-1/2" is (pending) // Requires board construction with stalemate position
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :core:test --tests "de.eljachess.chess.model.PgnSpec" 2>&1 | tail -15
```

Expected: compilation error — `Pgn` does not exist yet.

- [ ] **Step 3: Implement `Pgn.scala` with header generation**

Create `core/src/main/scala/de/eljachess/chess/model/Pgn.scala`:

```scala
// core/src/main/scala/de/eljachess/chess/model/Pgn.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Pgn:

  def encode(history: List[(GameController, de.eljachess.chess.controller.ParsedMove)],
             whiteName: String,
             blackName: String,
             currentPosition: GameController): String =
    val headers = buildHeaders(whiteName, blackName, currentPosition)
    val moveList = buildMoveList(history, currentPosition)
    val result = detectResult(currentPosition)
    s"$headers\n$moveList $result"

  private def buildHeaders(whiteName: String,
                          blackName: String,
                          currentPosition: GameController): String =
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    val result = detectResult(currentPosition)
    s"""[Event "?"]
       |[Site "?"]
       |[Date "$today"]
       |[Round "?"]
       |[White "$whiteName"]
       |[Black "$blackName"]
       |[Result "$result"]""".stripMargin

  private def detectResult(ctrl: GameController): String =
    val nextToMove = ctrl.currentTurn
    val hasMoves = ctrl.board.legalMoves(nextToMove).nonEmpty
    val inCheck = ctrl.board.isInCheck(nextToMove)
    if !hasMoves && inCheck then
      if nextToMove == Color.White then "0-1" else "1-0"
    else if !hasMoves then
      "1/2-1/2"
    else
      "*"

  private def buildMoveList(history: List[(GameController, de.eljachess.chess.controller.ParsedMove)]): String =
    // TODO: implement in Task 2
    ""
```

- [ ] **Step 4: Run tests to verify headers pass**

```bash
./gradlew :core:test --tests "de.eljachess.chess.model.PgnSpec" 2>&1 | tail -15
```

Expected: header tests pass; move list tests will fail (not yet implemented).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/model/Pgn.scala \
        core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
git commit -m "feat: add Pgn.encode with header generation and result detection"
```

---

### Task 2: SAN Generation and Move List Encoding

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/model/Pgn.scala`
- Modify: `core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala`

- [ ] **Step 1: Add SAN move tests to PgnSpec**

Append to `PgnSpec.scala`:

```scala
  // ── SAN generation ─────────────────────────────────────────────────────

  "Pgn.sanForMove" should "convert pawn move e2-e4 to SAN \"e4\"" in {
    val board = Board.initial
    val move = de.eljachess.chess.controller.ParsedMove.Move(Square(4, 1), Square(4, 3), None)
    val boardAfter = board.move(Square(4, 1), Square(4, 3), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e4"
  }

  it should "convert pawn capture e4xd5 to SAN \"exd5\"" in {
    // Construct board with white pawn on e4, black pawn on d5
    val grid = scala.collection.mutable.Map[Square, Piece](
      Square(4, 3) -> Piece(Color.White, PieceKind.Pawn),
      Square(3, 4) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val board = Board(grid.toMap)
    val move = de.eljachess.chess.controller.ParsedMove.Move(Square(4, 3), Square(3, 4), None)
    val boardAfter = board.move(Square(4, 3), Square(3, 4), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "exd5"
  }

  it should "convert knight move g1-f3 to SAN \"Nf3\"" in {
    val board = Board.initial
    val move = de.eljachess.chess.controller.ParsedMove.Move(Square(6, 0), Square(5, 2), None)
    val boardAfter = board.move(Square(6, 0), Square(5, 2), None).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "Nf3"
  }

  it should "convert castling kingside to SAN \"O-O\"" in {
    val board = Board.initial
    val move = de.eljachess.chess.controller.ParsedMove.Castling(kingside = true)
    val boardAfter = board  // dummy, not used for castling
    Pgn.sanForMove(board, move, boardAfter) shouldBe "O-O"
  }

  it should "convert castling queenside to SAN \"O-O-O\"" in {
    val board = Board.initial
    val move = de.eljachess.chess.controller.ParsedMove.Castling(kingside = false)
    val boardAfter = board
    Pgn.sanForMove(board, move, boardAfter) shouldBe "O-O-O"
  }

  it should "convert pawn promotion e7-e8=Q to SAN \"e8=Q\"" in {
    val grid = scala.collection.mutable.Map[Square, Piece](
      Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)
    )
    val board = Board(grid.toMap)
    val move = de.eljachess.chess.controller.ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen))
    val boardAfter = board.move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen)).get
    Pgn.sanForMove(board, move, boardAfter) shouldBe "e8=Q"
  }

  it should "format move list for short game as \"1. e4 e5 2. Nf3 Nc6\"" in {
    // Encode initial position + 4 moves
    // This test requires GameManager with move tracking (Task 3)
    // For now, test with mock data
    val history = List() // TODO: populate with e4, e5, Nf3, Nc6
    val ctrl = GameController(Board.initial)
    val pgn = Pgn.encode(history, "White", "Black", ctrl)
    pgn should include("1. e4 e5")
    pgn should include("2. Nf3 Nc6")
  }
```

- [ ] **Step 2: Implement `sanForMove` for all move types**

Replace the `buildMoveList` stub in `Pgn.scala` with full implementation:

```scala
  private def sanForMove(boardBefore: Board,
                        move: de.eljachess.chess.controller.ParsedMove,
                        boardAfter: Board): String = move match
    case de.eljachess.chess.controller.ParsedMove.Move(from, to, promotion) =>
      sanForPieceMove(boardBefore, from, to, promotion, boardAfter)
    case de.eljachess.chess.controller.ParsedMove.Castling(kingside) =>
      if kingside then "O-O" else "O-O-O"
    case de.eljachess.chess.controller.ParsedMove.FenQuery | de.eljachess.chess.controller.ParsedMove.FenLoad(_) | de.eljachess.chess.controller.ParsedMove.PgnQuery =>
      throw new Exception("Non-move command in PGN history: should never occur")

  private def sanForPieceMove(boardBefore: Board,
                             from: Square,
                             to: Square,
                             promotion: Option[PieceKind],
                             boardAfter: Board): String =
    val piece = boardBefore.pieceAt(from).get
    val movingColor = piece.color
    val nextColor = if movingColor == Color.White then Color.Black else Color.White

    val isCapture = boardBefore.pieceAt(to).isDefined

    // For pawns, use file of origin (from.toAlgebraic.head) + 'x' if capture, else destination only
    val moveStr = if piece.kind == PieceKind.Pawn then
      if isCapture then
        s"${from.toAlgebraic.head}x${to.toAlgebraic}"
      else
        to.toAlgebraic
    else
      // For non-pawns: piece letter + capture marker + destination
      val pieceStr = piece.kind match
        case PieceKind.Knight => "N"
        case PieceKind.Bishop => "B"
        case PieceKind.Rook => "R"
        case PieceKind.Queen => "Q"
        case PieceKind.King => "K"
        case _ => ""
      val captureStr = if isCapture then "x" else ""
      s"$pieceStr$captureStr${to.toAlgebraic}"

    val promStr = promotion.map(k => s"=${pieceChar(k)}").getOrElse("")

    val checkStr =
      val inCheck = boardAfter.isInCheck(nextColor)
      val hasMoves = boardAfter.legalMoves(nextColor).nonEmpty
      if inCheck && !hasMoves then "#"
      else if inCheck then "+"
      else ""

    s"$moveStr$promStr$checkStr"

  private def pieceChar(kind: PieceKind): String = kind match
    case PieceKind.Queen => "Q"
    case PieceKind.Rook => "R"
    case PieceKind.Bishop => "B"
    case PieceKind.Knight => "N"
    case _ => ""

  // Note: This method is incomplete without the currentPosition parameter.
  // See the encode method for how to handle the last move's boardAfter.
  private def buildMoveList(history: List[(GameController, de.eljachess.chess.controller.ParsedMove)],
                           currentPosition: GameController): String =
    if history.isEmpty then ""
    else
      val moves = history.zipWithIndex.map { case ((ctrl, move), idx) =>
        val boardBefore = ctrl.board
        val boardAfter = if idx + 1 < history.length then
          history(idx + 1)._1.board
        else
          currentPosition.board  // last move: use current position's board
        sanForMove(boardBefore, move, boardAfter)
      }
      formatMoveList(moves)

  private def formatMoveList(moves: List[String]): String =
    moves.zipWithIndex.map { case (move, idx) =>
      val moveNum = (idx / 2) + 1
      if idx % 2 == 0 then s"$moveNum. $move"
      else move
    }.grouped(2).map(_.mkString(" ")).mkString(" ")
```

- [ ] **Step 3: Run SAN tests**

```bash
./gradlew :core:test --tests "de.eljachess.chess.model.PgnSpec" 2>&1 | tail -20
```

Expected: SAN tests pass; move list tests still fail (need GameManager changes).

- [ ] **Step 4: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/model/Pgn.scala \
        core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
git commit -m "feat: add sanForMove and move list formatting for PGN export"
```

---

### Task 3: Extend GameManager History Tracking

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/controller/GameManager.scala`

- [ ] **Step 1: Update history structure in GameManager**

Replace in `GameManager.scala` (lines 5–8):

```scala
class GameManager(initial: GameController):
  private var current   = initial
  private var history   = List.empty[(GameController, ParsedMove)]
  private var future    = List.empty[(GameController, ParsedMove)]
  private val observers = mutable.Buffer.empty[Observer]
```

- [ ] **Step 2: Update `move()` method to track ParsedMove**

Replace the `move()` method (lines 16–28):

```scala
def move(input: String, caller: Observer | Null = null): String =
  val (snapshot, ctrl, msg) = synchronized {
    val parsed = CommandParser.parse(input)
    val (next, msg) = current.handleCommand(input)
    if next != current then
      parsed match
        case Right(parsedMove) =>
          history = (current, parsedMove) :: history
          future  = Nil
          current = next
          (observers.toList.filterNot(_ eq caller), current, msg)
        case Left(_) =>
          (Nil, current, msg)
    else
      (Nil, current, msg)
  }
  snapshot.foreach(_.onUpdate(ctrl, msg))
  msg
```

- [ ] **Step 3: Update `undo()` method for tuple structure**

Replace the `undo()` method (lines 30–42):

```scala
def undo(caller: Observer | Null = null): String =
  val result = synchronized {
    history match
      case Nil          => (Nil, current, "Nothing to undo")
      case (prev, move) :: rest =>
        future  = (current, move) :: future
        history = rest
        current = prev
        (observers.toList.filterNot(_ eq caller), current, "Undo")
  }
  val (snapshot, ctrl, msg) = result
  if snapshot.nonEmpty then snapshot.foreach(_.onUpdate(ctrl, msg))
  msg
```

- [ ] **Step 4: Update `redo()` method for tuple structure**

Replace the `redo()` method (lines 44–56):

```scala
def redo(caller: Observer | Null = null): String =
  val result = synchronized {
    future match
      case Nil          => (Nil, current, "Nothing to redo")
      case (next, move) :: rest =>
        history = (current, move) :: history
        future  = rest
        current = next
        (observers.toList.filterNot(_ eq caller), current, "Redo")
  }
  val (snapshot, ctrl, msg) = result
  if snapshot.nonEmpty then snapshot.foreach(_.onUpdate(ctrl, msg))
  msg
```

- [ ] **Step 5: Run existing tests to verify no regressions**

```bash
./gradlew :core:test 2>&1 | tail -15
```

Expected: all existing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/controller/GameManager.scala
git commit -m "feat: extend GameManager history to track (GameController, ParsedMove) tuples"
```

---

### Task 4: Add `pgn()` Method to GameManager

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/controller/GameManager.scala`

- [ ] **Step 1: Add `pgn()` public method**

Append to `GameManager`:

```scala
  def pgn(whiteName: String, blackName: String): String =
    synchronized {
      Pgn.encode(history.reverse, whiteName, blackName, current)
    }
```

- [ ] **Step 2: Add required imports**

Add to imports (line 4):

```scala
import de.eljachess.chess.model.Pgn
```

- [ ] **Step 3: Run tests**

```bash
./gradlew :core:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/controller/GameManager.scala
git commit -m "feat: add pgn() method to GameManager for PGN export"
```

---

### Task 5: GUI "Export PGN" Button

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala`

- [ ] **Step 1: Update imports**

Replace line 8:

```scala
import javafx.scene.control.{Button, ChoiceDialog, Label, TextInputDialog}
import javafx.scene.input.{Clipboard, ClipboardContent}
import scala.jdk.OptionConverters.*
```

(Keep the old ChoiceDialog import for promotion dialog.)

- [ ] **Step 2: Add "Export PGN" button to toolbar**

In `buildScene()`, find the toolbar block (around lines 49–57) and replace with:

```scala
    val undoBtn = Button("Undo")
    val redoBtn = Button("Redo")
    undoBtn.setOnAction(_ => doAction(manager.undo(this)))
    redoBtn.setOnAction(_ => doAction(manager.redo(this)))

    val copyFenBtn = Button("Copy FEN")
    copyFenBtn.setOnAction { _ =>
      val fenStr  = manager.move("fen", this)
      val content = new ClipboardContent()
      content.putString(fenStr)
      Clipboard.getSystemClipboard.setContent(content)
      msgLabel.setText("FEN copied")
    }

    val loadFenBtn = Button("Load FEN")
    loadFenBtn.setOnAction { _ =>
      val dialog = new TextInputDialog()
      dialog.setTitle("FEN laden")
      dialog.setHeaderText("FEN-String eingeben")
      dialog.setContentText("FEN:")
      dialog.showAndWait().toScala match
        case Some(input) if input.nonEmpty =>
          val msg = manager.move(s"load $input", this)
          selected = None; currentCtrl = manager.state; redrawBoard(currentCtrl); msgLabel.setText(msg)
        case _ => ()
    }

    val exportPgnBtn = Button("Export PGN")
    exportPgnBtn.setOnAction { _ =>
      val dialog = new TextInputDialog()
      dialog.setTitle("Spieler eingeben")
      dialog.setHeaderText("Spielernamen für PGN")
      dialog.setContentText("Weiß, Schwarz (kommagetrennt):")
      dialog.showAndWait().toScala match
        case Some(input) if input.nonEmpty =>
          val names = input.split(",").map(_.trim)
          if names.length == 2 then
            val pgnStr = manager.pgn(names(0), names(1))
            val content = new ClipboardContent()
            content.putString(pgnStr)
            Clipboard.getSystemClipboard.setContent(content)
            msgLabel.setText("PGN copied")
          else
            msgLabel.setText("Format: White, Black")
        case _ => ()
    }

    msgLabel.setPadding(Insets(0, 8, 0, 8))
    val toolbar = HBox(8.0, undoBtn, redoBtn, copyFenBtn, loadFenBtn, exportPgnBtn, msgLabel)
    toolbar.setPadding(Insets(8))
    toolbar.setAlignment(Pos.CENTER_LEFT)
    root.setBottom(toolbar)
```

- [ ] **Step 3: Verify build compiles**

```bash
./gradlew :core:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala
git commit -m "feat: add Export PGN button to GUI toolbar"
```

---

### Task 6: Final Testing and Coverage Check

**Files:**
- None (verification only)

- [ ] **Step 1: Run full test suite**

```bash
./gradlew :core:test 2>&1 | tail -20
```

Expected: All tests pass, including new PGN tests.

- [ ] **Step 2: Check coverage for Pgn.scala**

```bash
./gradlew :core:scoverageTest 2>&1 | tail -5
python jacoco-reporter/scoverage_coverage_gaps.py core/build/reports/scoverageTest/scoverage.xml
```

Expected: `Pgn.scala` ≥ 95% line / ≥ 90% branch coverage.

- [ ] **Step 3: Build check**

```bash
./gradlew :core:build 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL, no warnings.

- [ ] **Step 4: Final commit message summary**

Run:
```bash
git log --oneline -6
```

Expected: 6 commits:
1. "feat: add Pgn.encode with header generation and result detection"
2. "feat: add sanForMove and move list formatting for PGN export"
3. "feat: extend GameManager history to track (GameController, ParsedMove) tuples"
4. "feat: add pgn() method to GameManager for PGN export"
5. "feat: add Export PGN button to GUI toolbar"
6. (This summary commit if needed)

---

## Notes

**Deferred requirements:** The spec lists adding `ParsedMove.PgnQuery`, `CommandParser` support, and `GameController` handling as "reserved for future TUI support". These are intentionally not included in this plan since the current sub-project focuses on GUI export only. A future TUI support task will add the command pipeline integration.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-03-31-pgn-export.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
