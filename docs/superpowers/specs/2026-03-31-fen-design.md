# FEN Import/Export — Design Spec

**Date:** 2026-03-31
**Status:** Draft
**Sub-Project:** 2 of 4 (Chess Rules → FEN → PGN Export → PGN Import)

## Overview

Add FEN (Forsyth–Edwards Notation) export and import to the chess engine, TUI, and GUI. A pure `Fen` object handles encoding and decoding. The existing `ParsedMove` ADT and `CommandParser` are extended with two new command types (`FenQuery`, `FenLoad`). The GUI gains two toolbar buttons.

## Requirements

1. **FEN export:** `Fen.encode(ctrl: GameController): String` produces a valid 6-field FEN string from the current game state.
2. **FEN import:** `Fen.decode(fen: String): Either[String, GameController]` parses a FEN string and returns a `GameController` or a descriptive error.
3. **Round-trip:** `Fen.decode(Fen.encode(ctrl)) == Right(ctrl)` for any valid game state.
4. **Known position:** `Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")` returns `Right(GameController(Board.initial))`.
5. **TUI commands:** `"fen"` prints the current FEN string; `"load <fen>"` replaces the game state.
6. **GUI buttons:** "Copy FEN" copies the current FEN to the system clipboard; "Load FEN" shows a `TextInputDialog` and loads the entered string.
7. **Error handling:** `Fen.decode` returns `Left("Invalid FEN: <reason>")` on any malformed input; the GUI/TUI shows the error without changing state.

## Scope

**Note:** This project uses `core/` as the single module name, predating the `modules/` convention in CLAUDE.md. All paths below are intentionally under `core/`.

**New files:**
- `core/src/main/scala/de/eljachess/chess/model/Fen.scala`
- `core/src/test/scala/de/eljachess/chess/model/FenSpec.scala`

**Modified files:**
- `core/src/main/scala/de/eljachess/chess/controller/ParsedMove.scala` — two new cases
- `core/src/main/scala/de/eljachess/chess/controller/CommandParser.scala` — two new patterns
- `core/src/main/scala/de/eljachess/chess/controller/GameController.scala` — two new match arms
- `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` — two toolbar buttons (scoverage-excluded)
- `core/src/test/scala/de/eljachess/chess/controller/CommandParserSpec.scala` — 3 new cases
- `core/src/test/scala/de/eljachess/chess/controller/GameControllerSpec.scala` — 2 new cases

## Data Model

All data needed for FEN is already present in `GameController`:

```
GameController(
  board:          Board(grid, castlingRights, enPassantTarget),
  currentTurn:    Color,
  halfmoveClock:  Int,
  fullmoveNumber: Int
)
```

## Design

### FEN Format

A FEN string has exactly 6 space-separated fields:

```
<piece-placement> <active-color> <castling> <en-passant> <halfmove> <fullmove>
```

Example (initial position):
```
rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
```

### `Fen.encode`

```scala
object Fen:
  def encode(ctrl: GameController): String
```

Assumes all fields of `GameController` satisfy their invariants (non-negative clocks, valid board). No defensive handling for invalid inputs.

Encodes each field:

1. **Piece placement** — iterates rows 7→0 (rank 8→1), cols 0→7 (file a→h). White pieces are uppercase (`KQRBNP`), Black pieces lowercase (`kqrbnp`). Consecutive empty squares are replaced by their count (1–8). Ranks are joined with `"/"`.

2. **Active color** — `"w"` if `currentTurn == Color.White`, else `"b"`.

3. **Castling** — builds a string from `castlingRights`: `K` if `whiteKingside`, `Q` if `whiteQueenside`, `k` if `blackKingside`, `q` if `blackQueenside`. Returns `"-"` if none apply.

4. **En passant** — `enPassantTarget.map(_.toAlgebraic).getOrElse("-")`.

5. **Halfmove clock** — `halfmoveClock.toString`.

6. **Fullmove number** — `fullmoveNumber.toString`.

### `Fen.decode`

```scala
  def decode(fen: String): Either[String, GameController]
```

Splits the input on whitespace into tokens. Returns `Left("Invalid FEN: <reason>")` if any check fails:

- **Field count:** tokens.length != 6 → `"Invalid FEN: expected 6 fields, got <n>"`
- **Piece placement:** any character not in `KQRBNPkqrbnp1-8/` → `"Invalid FEN: invalid piece char '<c>'"`. Rank count (split by `/`) != 8 → `"Invalid FEN: expected 8 ranks, got <n>"`. Any rank's square sum != 8 → `"Invalid FEN: rank <n> has wrong length"`.
- **Active color:** not `"w"` or `"b"` → `"Invalid FEN: invalid active color '<s>'"`.
- **Castling:** contains a character outside `KQkq-` → `"Invalid FEN: invalid castling '<s>'"`.
- **En passant:** not `"-"` and not matching `[a-h][1-8]` → `"Invalid FEN: invalid en passant square '<s>'"`.
- **Halfmove clock:** not parseable as non-negative integer → `"Invalid FEN: invalid halfmove clock '<s>'"`.
- **Fullmove number:** not parseable as positive integer → `"Invalid FEN: invalid fullmove number '<s>'"`.

The `load` command passes the raw FEN string (everything after `"load "`) directly to `Fen.decode`. Since a FEN string itself contains spaces, `CommandParser` extracts the FEN by stripping the `"load "` prefix from the trimmed input — **not** by splitting on whitespace.

On success, reconstructs:
- `Board(grid, castlingRights, enPassantTarget)`
- `GameController(board, currentTurn, halfmoveClock, fullmoveNumber)`

### `ParsedMove` extension

```scala
enum ParsedMove:
  case Move(from: Square, to: Square, promotion: Option[PieceKind])
  case Castling(kingside: Boolean)
  case FenQuery                 // input: "fen"
  case FenLoad(fen: String)     // input: "load <fen-string>"
```

### `CommandParser` patterns

Added at the **top** of `parse`, before the castling checks and `tokens match` block:

```scala
def parse(input: String): Either[String, ParsedMove] =
  val trimmed = input.trim
  if trimmed == "fen" then return Right(ParsedMove.FenQuery)
  if trimmed.startsWith("load ") then
    val fen = trimmed.stripPrefix("load ").trim
    return Right(ParsedMove.FenLoad(fen))
  if trimmed == "load" then return Left("Usage: load <fen>")
  // ... existing castling and move parsing below
```

| Input | Result |
|-------|--------|
| `"fen"` | `Right(ParsedMove.FenQuery)` |
| `"load <s>"` (non-empty `s`) | `Right(ParsedMove.FenLoad(s))` |
| `"load"` (no argument) | `Left("Usage: load <fen>")` |

### `GameController.handleCommand` additions

The FEN cases are added **before** the coordinate-extraction logic, as direct top-level match arms on `Right(parsed)`:

```scala
def handleCommand(input: String): (GameController, String) =
  CommandParser.parse(input) match
    case Left(err)                     => (this, err)
    case Right(ParsedMove.FenQuery)    => (this, Fen.encode(this))
    case Right(ParsedMove.FenLoad(s))  =>
      Fen.decode(s) match
        case Right(newCtrl) => (newCtrl, "Position loaded")
        case Left(err)      => (this, err)
    case Right(parsed) =>
      val (from, to, promo) = parsed match
        case ParsedMove.Move(f, t, p)      => (f, t, p)
        case ParsedMove.Castling(kingside) =>
          val row   = if currentTurn == Color.White then 0 else 7
          val toCol = if kingside then 6 else 2
          (Square(4, row), Square(toCol, row), None)
      // ... rest of existing move logic
```

### GUI changes

Two new buttons added to the existing toolbar `HBox` after the Redo button, with consistent spacing:

**Required additional imports:**
```scala
import javafx.scene.control.{Button, ChoiceDialog, Label, TextInputDialog}
import javafx.scene.input.{Clipboard, ClipboardContent}
import scala.jdk.OptionConverters.*
```

**"Copy FEN"**
```scala
val copyFenBtn = Button("Copy FEN")
copyFenBtn.setOnAction { _ =>
  val fenStr = manager.move("fen", this)
  val clipboard = Clipboard.getSystemClipboard
  val content = new ClipboardContent()
  content.putString(fenStr)
  clipboard.setContent(content)
  msgLabel.setText("FEN copied")
}
```

**"Load FEN"**
```scala
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
```

Both buttons are appended to the existing `HBox` toolbar alongside Undo and Redo.

## Testing

### `FenSpec` — test groups (ScalaTest `AnyFlatSpec with Matchers`)

| Group | Key cases |
|-------|-----------|
| Encode — piece placement | Initial position produces `"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"`; empty rank encodes as `"8"`; single White King on e1 encodes correctly |
| Encode — state fields | White to move → `"w"`; Black to move → `"b"`; all rights → `"KQkq"`; partial rights (only `whiteKingside` + `blackQueenside`) → `"Kq"`; no rights → `"-"`; en passant set → algebraic square; en passant absent → `"-"`; halfmoveClock = 7 encoded as `"7"`; fullmoveNumber = 3 encoded as `"3"` |
| Decode — round-trip | `Fen.decode(Fen.encode(ctrl))` returns `Right(ctrl)` for initial position; round-trip preserves `halfmoveClock` and `fullmoveNumber` for a mid-game `GameController` |
| Decode — known string | `"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"` → `Right(GameController(Board.initial))` |
| Decode — errors | Wrong field count (5 or 7); invalid piece char (`X`); rank sum ≠ 8; rank count ≠ 8; bad active color (`"x"`); bad castling char (`"Z"`); bad en passant (`"e9"`); non-integer halfmove clock (`"x"`); non-integer fullmove number (`"0"`) |

Example test structure:
```scala
"Fen.encode" should "produce the initial FEN piece placement" in { ... }
it should "encode White to move as 'w'" in { ... }
"Fen.decode" should "round-trip the initial position" in { ... }
it should "return Left for wrong field count" in { ... }
```

### `CommandParserSpec` additions

```scala
it should "parse fen command" in {
  CommandParser.parse("fen") shouldBe Right(ParsedMove.FenQuery)
}
it should "parse load command with FEN string" in {
  CommandParser.parse("load rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
    Right(ParsedMove.FenLoad("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
}
it should "return Left for load without argument" in {
  CommandParser.parse("load") shouldBe Left("Usage: load <fen>")
}
```

### `GameControllerSpec` additions

```scala
it should "return current FEN string for fen command" in {
  val (_, msg) = GameController(Board.initial).handleCommand("fen")
  msg shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
}
it should "reset to initial position on load command" in {
  val (afterMove, _) = GameController(Board.initial).handleCommand("e2 e4")
  val (reset, msg) = afterMove.handleCommand(
    "load rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  )
  reset.board shouldBe Board.initial
  msg shouldBe "Position loaded"
}
```

## Known Limitations

- FEN import does not validate that the position is legal (e.g. two kings required, pawns not on back ranks). It only validates format.
- The 50-move rule draw is not enforced; `halfmoveClock` is tracked for FEN only.

## Non-Goals

- PGN export/import (Sub-Projects 3 & 4)
- FEN validation beyond format checking
