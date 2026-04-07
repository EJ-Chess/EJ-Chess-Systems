# JSON Import/Export Design

**Date:** 2026-04-07  
**Branch:** dev  

---

## Goal

Add JSON import and export of the current game position (FEN + metadata) to the chess application, integrated into both the TUI and the GUI.

---

## JSON Format

A single JSON object with four string fields:

```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "whiteName": "Player 1",
  "blackName": "Player 2",
  "date": "2026-04-07"
}
```

- `fen` — the full FEN string of the current position, produced by `Fen.encode`
- `whiteName` / `blackName` — player names (metadata only, not loaded back into game state)
- `date` — ISO date string at export time (`java.time.LocalDate.now().toString`)

On import, only `fen` is used. The other fields are parsed but ignored.

---

## Architecture

### New file: `core/src/main/scala/de/eljachess/chess/model/Json.scala`

Single object, following the same pattern as `Fen.scala` and `Pgn.scala`.

**Encode:**
```scala
def encode(ctrl: GameController, whiteName: String = "White", blackName: String = "Black"): String
```
Produces a pretty-printed JSON string. Uses `Fen.encode` internally.

**Decode:**
```scala
val decode: String => Either[String, GameController]
```
Parses the JSON string, extracts the `fen` field, delegates to `Fen.decode`. Returns `Left` if any required field is missing or the FEN is invalid.  
Exposed as a `val` (first-class function) for consistency with `Fen.decode`.

**Parsing strategy:** hand-crafted — extract fields via regex `"field"\s*:\s*"(value)"` for each known key. No external library needed for a 4-field object.

---

### TUI changes: `core/src/main/scala/de/eljachess/chess/tui/TUI.scala`

Two new commands handled directly in `TUI.loop()`, alongside the existing `undo`/`redo` branches:

| Command | Behaviour |
|---|---|
| `save-json <filename>` | Encodes current state with default player names, writes to file, prints `"Saved to <filename>"` |
| `load-json <filename>` | Reads file, decodes JSON, loads position via `manager.move("load <fen>", this)`, prints `"Loaded from <filename>"` or error |

File I/O errors (file not found, permission denied) are caught and printed as `"Error: <message>"`.

---

### GUI changes: `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala`

Two new buttons added to `btnGrid` at row 3 (below the existing PGN buttons at row 2):

| Button | Behaviour |
|---|---|
| `"Export JSON"` | Opens `FileChooser` with `.json` filter, prompts for player names via `TextInputDialog` (same pattern as Export PGN), writes JSON file, sets `msgLabel` to `"JSON exported"` |
| `"Import JSON"` | Opens `FileChooser` with `.json` filter, reads file, calls `Json.decode`, loads position via `manager.move("load <fen>", this)`, sets `msgLabel` to `"JSON imported"` or error |

Error handling: on failure, sets `msgLabel` to `"JSON error: <message>"`. Matches the pattern of `buildImportPgnButton`.  
Both buttons wrapped in `// $COVERAGE-OFF$` / `// $COVERAGE-ON$` (same as PGN buttons — JavaFX lifecycle cannot be headless-tested).

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| Missing `fen` field in JSON | `Left("Invalid JSON: missing field 'fen'")` |
| Invalid FEN in `fen` field | Propagates `Fen.decode` error, e.g. `Left("Invalid FEN: ...")` |
| Malformed JSON (unmatched quotes) | `Left("Invalid JSON: malformed")` |
| File not found (TUI) | Caught, printed as `"Error: <filename>: No such file"` |
| File not found (GUI) | `msgLabel.setText("JSON error: file not found")` |

---

## Testing

- `JsonSpec` in `core/src/test/scala/de/eljachess/chess/model/JsonSpec.scala`
- Tests cover: encode round-trip, decode valid JSON, decode missing `fen` field, decode invalid FEN, decode malformed JSON
- TUI file I/O tested with an in-memory string (same pattern as existing TUI tests if any, otherwise mock filesystem with `java.nio.file`)
- GUI buttons excluded from coverage (`$COVERAGE-OFF$`)

---

## Files Created / Modified

| Action | File |
|---|---|
| Create | `core/src/main/scala/de/eljachess/chess/model/Json.scala` |
| Create | `core/src/test/scala/de/eljachess/chess/model/JsonSpec.scala` |
| Modify | `core/src/main/scala/de/eljachess/chess/tui/TUI.scala` |
| Modify | `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` |
