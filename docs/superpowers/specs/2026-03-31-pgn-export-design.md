# PGN Export — Design Spec

**Date:** 2026-03-31
**Status:** Draft
**Sub-Project:** 3 of 4 (Chess Rules → FEN → **PGN Export** → PGN Import)

## Overview

Add PGN (Portable Game Notation) export to the chess engine, TUI, and GUI. A pure `Pgn` object handles encoding the full game history to a standard 7-tag PGN string with move notation in Standard Algebraic Notation (SAN). The GUI gains an "Export PGN" button that prompts for player names and copies the result to the system clipboard.

## Requirements

1. **PGN export:** `Pgn.encode(history: List[(GameController, ParsedMove)], whiteName: String, blackName: String, currentPosition: GameController): String` produces a valid PGN string with 7-tag headers and move list in SAN.
2. **SAN generation:** Each move is converted to Standard Algebraic Notation (e.g., `"e4"`, `"Nf3"`, `"Nxe5"`, `"O-O"`, `"e8=Q"`, `"Qh5#"`).
3. **Headers:** PGN includes the Seven Tag Roster: Event, Site, Date (YYYY.MM.DD format of system clock at time of export), Round, White (user-provided), Black (user-provided), Result (auto-detected: `"*"` in progress, `"1-0"` White wins, `"0-1"` Black wins, `"1/2-1/2"` draw).
4. **GUI export:** "Export PGN" button opens a dialog prompting for White and Black player names; on completion, copies PGN to system clipboard and displays "PGN copied".
5. **GameManager tracking:** `GameManager` stores each historical move alongside its position via `List[(GameController, ParsedMove)]` to reconstruct the move sequence for SAN generation.
6. **Error handling:** Invalid moves or missing data result in graceful error messages without corrupting state.

## Scope

**Note:** This project uses `core/` as the single module name, predating the `modules/` convention in CLAUDE.md. All paths below are intentionally under `core/`.

**New files:**
- `core/src/main/scala/de/eljachess/chess/model/Pgn.scala`
- `core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala`

**Modified files:**
- `core/src/main/scala/de/eljachess/chess/controller/GameManager.scala` — extend history tracking to `List[(GameController, ParsedMove)]`
- `core/src/main/scala/de/eljachess/chess/controller/ParsedMove.scala` — add `PgnQuery` case (reserved for future TUI support)
- `core/src/main/scala/de/eljachess/chess/controller/CommandParser.scala` — parse `pgn <whiteName> <blackName>` (reserved for future TUI support)
- `core/src/main/scala/de/eljachess/chess/controller/GameController.scala` — handle `PgnQuery` case (reserved for future TUI support)
- `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` — "Export PGN" button (scoverage-excluded)

**Not included in this sub-project:**
- PGN import / game replay (Sub-Project 4)
- Draw by agreement or resignation (game result detection uses only position-derived outcomes: checkmate, stalemate, or in-progress `"*"`)

## Data Model

All data needed for PGN export is present in the sequence of `(GameController, ParsedMove)` pairs:

```
List[
  (GameController_1, ParsedMove_1),  // state before move 1, the move itself
  (GameController_2, ParsedMove_2),  // state before move 2, the move itself
  ...
]
current: GameController              // current position (after last move)
```

From consecutive pairs, `Pgn.encode()` can reconstruct the full move sequence with SAN notation.

## Design

### GameManager History Extension

Current structure:
```scala
private var history: List[GameController]
private var future: List[GameController]
private var current: GameController
```

Extended structure:
```scala
private var history: List[(GameController, ParsedMove)]
private var future: List[(GameController, ParsedMove)]
private var current: GameController
```

When `move(input, caller)` is called:
1. Parse input to `ParsedMove`
2. Call `current.handleCommand(input)` → `(nextCtrl, msg)`
3. If state changed (`nextCtrl != current`):
   - Prepend `(current, parsedMove)` to `history`
   - Clear `future`
   - Set `current = nextCtrl`
4. Notify observers and return `msg`

The `undo()` and `redo()` methods work identically on the tuples.

### `Pgn.encode`

```scala
object Pgn:
  def encode(history: List[(GameController, ParsedMove)],
             whiteName: String,
             blackName: String,
             currentPosition: GameController): String
```

Algorithm:
1. Build the 7-tag header block:
   ```
   [Event "?"]
   [Site "?"]
   [Date "YYYY.MM.DD"]
   [Round "?"]
   [White "whiteName"]
   [Black "blackName"]
   [Result "result"]
   ```
   where:
   - `Date` is the system clock's local date in YYYY.MM.DD format at the time `encode` is called
   - `result` is determined from `currentPosition`:
     - If the player to move in `currentPosition` has no legal moves AND is in check → checkmate. Result is `"0-1"` if White to move (Black won), `"1-0"` if Black to move (White won).
     - If the player to move has no legal moves but is NOT in check → stalemate → `"1/2-1/2"`
     - Otherwise (legal moves available) → `"*"` (in progress)

2. Convert each move to SAN via `sanForMove(boardBefore, parsedMove, boardAfter)` (see SAN generation below).

3. Iterate over the history list. For each tuple `(ctrl, move)` at index i:
   - `boardBefore = ctrl.board` (the position before the move was played)
   - `boardAfter = history[i+1].1.board` (the position from the next tuple), OR apply `move` to `boardBefore` to compute it
   - Call `sanForMove(boardBefore, move, boardAfter)` to get the SAN string for this move
   - Append to the move list with standard numbering:
     ```
     1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 ...
     ```
     where odd-numbered moves (White) are formatted as `n. move` and even-numbered (Black) as `move` (no new line between pairs)

4. Append the result symbol at the end: ` 1-0`, ` 0-1`, ` 1/2-1/2`, or ` *`

5. Return the complete PGN string.

### SAN Generation

Private helper methods in the `Pgn` object:

```scala
private def opposite(color: Color): Color =
  if color == Color.White then Color.Black else Color.White

private def sanForMove(boardBefore: Board,
                       move: ParsedMove,
                       boardAfter: Board): String
```

For each `ParsedMove` case:

**`Move(from, to, promotion)`:**
1. Determine the piece at `from` on `boardBefore`. Let `movingColor` = color of that piece.
2. Apply the move to `boardBefore` to get `boardAfter`.
3. The next player to move is the opposite of `movingColor`.
4. Build the base move string:
   - **Pawn:** no piece letter. If capture: `file(from)x<dest>`, else `<dest>`
   - **Other pieces:** `<piece><disambiguation><capture><dest><promotion><check>`
     - `<piece>` = K, Q, R, B, N
     - `<disambiguation>` = empty unless two pieces of the same kind (same `movingColor`, same piece kind) can both reach `to` from their current positions on `boardBefore`. Then add file or rank of `from` to disambiguate. If both file and rank differ, add both.
     - `<capture>` = `x` if `boardBefore.pieceAt(to)` is non-empty, else empty
     - `<dest>` = algebraic notation of `to` (e.g., `"e5"`)
     - `<promotion>` = empty unless `promotion.isDefined`. Then `=<piece>` where piece is `promotion.get`
     - `<check>` = `#` if `boardAfter.isInCheck(opposite(movingColor))` and `boardAfter.legalMoves(opposite(movingColor)).isEmpty` (checkmate), else `+` if `boardAfter.isInCheck(opposite(movingColor))` (check), else empty

5. Return the built string.

**`Castling(kingside)`:**
- Return `"O-O"` if kingside, `"O-O-O"` if queenside.

**`FenQuery`, `FenLoad`, `PgnQuery`:**
- Not move commands. Should never appear in the game history (they do not change game state). If `Pgn.encode` encounters them during iteration, it must raise an exception with a clear error message (defensive approach to catch bugs early).

### GUI Changes

The GUI gains one new button in the toolbar.

**Required additional imports:**
```scala
import javafx.scene.control.{Button, ChoiceDialog, Label, TextInputDialog}
import javafx.scene.input.{Clipboard, ClipboardContent}
import scala.jdk.OptionConverters.*
```

**"Export PGN" button** (added to the toolbar after "Load FEN" button):
```scala
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
```

The button is appended to the existing toolbar alongside Undo, Redo, Copy FEN, Load FEN, and the message label.

### GameManager Public Method

Add to `GameManager`:
```scala
def pgn(whiteName: String, blackName: String): String =
  synchronized {
    Pgn.encode(history.reverse, whiteName, blackName, current)
  }
```

This passes the full game history (oldest move first) and the current position, which allows `Pgn.encode` to detect the game result correctly.

## Testing

### `PgnSpec` — test groups (ScalaTest `AnyFlatSpec with Matchers`)

| Group | Key cases |
|-------|-----------|
| SAN — pawn moves | `e2 e4` → `"e4"`; `e4 d5` (capture) → `"exd5"` |
| SAN — piece moves | `g1 f3` → `"Nf3"`; `f3 e5` (capture) → `"Nxe5"` |
| SAN — special moves | Castling kingside → `"O-O"`; castling queenside → `"O-O-O"`; promotion → `"e8=Q"`; check → `"Nf7+"`; checkmate → `"Qh5#"` |
| SAN — disambiguation | Two knights can go to d7: first move → `"Nbd7"` or `"N1d7"` depending on which knight; test both branches |
| PGN encoding — headers | Event/Site/Round are `"?"`; Date is today (format YYYY.MM.DD); White/Black are provided; Result is auto-detected from final position |
| PGN encoding — move list | Initial position + moves e2-e4, e7-e5, g1-f3, b8-c6 → `"1. e4 e5 2. Nf3 Nc6"` |
| PGN encoding — result detection | Checkmate in final position → `"1-0"` or `"0-1"`; stalemate → `"1/2-1/2"`; in progress → `"*"` |
| Round-trip validation (scoped) | Encode a short game (5-10 moves) and verify headers and move notation are correct (full round-trip import is Sub-Project 4) |

Example test structure:
```scala
"Pgn.sanForMove" should "convert e2-e4 pawn move to SAN \"e4\"" in { ... }
it should "convert g1-f3 knight move to SAN \"Nf3\"" in { ... }
"Pgn.encode" should "generate PGN headers with provided player names" in { ... }
it should "format move list with correct numbering" in { ... }
it should "detect checkmate result as \"1-0\" or \"0-1\"" in { ... }
```

## Known Limitations

- PGN export does not support draw by agreement, resignation, or time/stalemate claims — result is auto-detected from board state only.
- No move variants (comments, annotations, or lines) — only the main line.
- SAN disambiguation assumes each piece moves independently; no support for unusual disambiguation edge cases (e.g., pinned pieces producing ambiguity that SAN rules would require).

## Non-Goals

- PGN import / game replay (Sub-Project 4)
- Chess960 or variant support
- PGN database export / batch operations
