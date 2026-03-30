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

**New files:**
- `core/src/main/scala/de/eljachess/chess/model/Fen.scala`
- `core/src/test/scala/de/eljachess/chess/model/FenSpec.scala`

**Modified files:**
- `core/src/main/scala/de/eljachess/chess/controller/ParsedMove.scala` — two new cases
- `core/src/main/scala/de/eljachess/chess/controller/CommandParser.scala` — two new patterns
- `core/src/main/scala/de/eljachess/chess/controller/GameController.scala` — two new match arms
- `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` — two toolbar buttons (scoverage-excluded)
- `core/src/test/scala/de/eljachess/chess/controller/CommandParserSpec.scala` — 2 new cases
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

Splits the input on whitespace. Returns `Left("Invalid FEN: <reason>")` if:
- Field count is not exactly 6.
- Piece placement: any character is not in `KQRBNPkqrbnp1-8/`; any rank (split by `/`) does not sum to 8 (digits count as their value, piece letters as 1); rank count is not 8.
- Active color: not `"w"` or `"b"`.
- Castling: not a subset of `KQkq` characters (or `"-"`).
- En passant: not `"-"` and not a valid algebraic square (`a-h` followed by `1-8`).
- Halfmove clock: not a non-negative integer.
- Fullmove number: not a positive integer.

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

Matched before existing move logic:

| Input | Result |
|-------|--------|
| `"fen"` | `Right(ParsedMove.FenQuery)` |
| `"load <s>"` (non-empty `s`) | `Right(ParsedMove.FenLoad(s))` |
| `"load"` (no argument) | `Left("Usage: load <fen>")` |

### `GameController.handleCommand` additions

```scala
case ParsedMove.FenQuery =>
  (this, Fen.encode(this))

case ParsedMove.FenLoad(s) =>
  Fen.decode(s) match
    case Right(newCtrl) => (newCtrl, "Position loaded")
    case Left(err)      => (this, err)
```

### GUI changes

Two new buttons added to the toolbar in `ChessGUI` (after Redo):

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

Note: `dialog.showAndWait().toScala` requires `import scala.jdk.OptionConverters.*`.

## Testing

### `FenSpec` — test groups

| Group | Key cases |
|-------|-----------|
| Encode — piece placement | Initial position produces `"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"`; empty rank encodes as `"8"`; single White King on e1 encodes correctly |
| Encode — state fields | White to move → `"w"`; Black to move → `"b"`; all rights → `"KQkq"`; partial rights → `"Kq"`; no rights → `"-"`; en passant set → algebraic square; en passant absent → `"-"`; halfmove and fullmove numbers |
| Decode — round-trip | `Fen.decode(Fen.encode(ctrl))` returns `Right(ctrl)` for initial position and a mid-game position |
| Decode — known string | Initial FEN → `Right(GameController(Board.initial))` |
| Decode — errors | Wrong field count; invalid piece char; rank sum ≠ 8; bad active color; bad castling char; bad en passant square; negative halfmove clock |

### `CommandParserSpec` additions

- `"fen"` → `Right(ParsedMove.FenQuery)`
- `"load rnbq..."` → `Right(ParsedMove.FenLoad("rnbq..."))`
- `"load"` (no argument) → `Left("Usage: load <fen>")`

### `GameControllerSpec` additions

- `handleCommand("fen")` on initial board returns `Right` containing the initial FEN string
- `handleCommand("load <initial-fen>")` on a modified board returns `"Position loaded"` and resets to initial position

## Known Limitations

- FEN import does not validate that the position is legal (e.g. two kings required, pawns not on back ranks). It only validates format.
- The 50-move rule draw is not enforced; `halfmoveClock` is tracked for FEN only.

## Non-Goals

- PGN export/import (Sub-Projects 3 & 4)
- FEN validation beyond format checking
