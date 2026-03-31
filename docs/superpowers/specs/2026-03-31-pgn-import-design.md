# PGN Import Design Spec

**Goal:** Add PGN import capability to load standard PGN files, parse headers and moves in Standard Algebraic Notation (SAN), expand SAN to algebraic notation, and replay moves into the GameManager.

**Architecture:** Pipeline: File → Pgn.decode (parse headers + move list) → SanDecoder.expand (SAN→algebraic) → GameController.handleCommand (replay) → GameManager state update. User selects file via GUI, errors shown in dialog with move number and reason.

**Tech Stack:** Scala 3, JavaFX (FileChooser), ScalaTest (AnyFlatSpec), Gradle + scoverage.

---

## Components

### 1. Pgn.decode Extension
**Location:** `core/src/main/scala/de/eljachess/chess/model/Pgn.scala`

Extend the existing `Pgn` object with:

```scala
def decode(text: String): Either[String, (Map[String, String], List[String])]
```

**Behavior:**
- Parses raw PGN text (7-tag header block + move list)
- Returns header map (Event, Site, Date, Round, White, Black, Result) and list of SAN move strings
- Validates structure: header lines like `[Tag "value"]`, moves in algebraic/SAN notation
- Ignores comments `{}`, annotations `!`, `?`, `!!`, `!?`, and variations `()` (out of scope)
- Returns `Left(error)` if parsing fails (malformed header, incomplete move list, duplicate tag, etc.)

**Parsing Rules:**
- Header section: zero or more `[Tag "value"]` lines (must appear before moves)
- Move section: space-separated SAN moves, optionally with move numbers (1., 2., etc.) and result (`*`, `1-0`, `0-1`, `1/2-1/2`)
- Each line can contain multiple moves (no enforced line breaks)
- Case-sensitive for piece notation (N, B, R, Q, K)

**Error Messages:**
- "Invalid PGN format at line {N}: expected [Tag \"value\"]"
- "Missing required PGN tag: {White|Black|Event|Site|Date|Round|Result}"
- "Duplicate PGN tag: {Tag}"
- "PGN has header but no moves"
- "Unexpected characters in move list at line {N}"

**Notes:**
- Implementation can use regex for line parsing or simple string matching
- Must handle DOS/Unix line endings gracefully
- Empty move list is valid (e.g., abandoned game)

---

### 2. SanDecoder Object
**Location:** `core/src/main/scala/de/eljachess/chess/controller/SanDecoder.scala` (new)

**Purpose:** Expand SAN notation to algebraic `(Square, Square, Option[PieceKind])` tuple by greedy search over legal moves.

```scala
object SanDecoder:

  def expand(board: Board, san: String): Either[String, (Square, Square, Option[PieceKind])]
```

**Algorithm:**
1. Parse SAN syntax: extract piece prefix (N/B/R/Q/K or empty for pawn), disambiguation (file/rank or square), capture marker (x), destination square, promotion (=Q/R/B/N), check/mate suffix (+/#)
2. Identify destination square from SAN (e.g., "Nf3" → f3)
3. Get all legal moves from current board that reach destination
4. Filter by piece type (pawn if no prefix, else matching piece)
5. If multiple candidates remain, disambiguate:
   - If file specified (e.g., "Nbd2") → keep only moves from that file
   - Else if rank specified (e.g., "N2d4") → keep only moves from that rank
   - Else → error (ambiguous)
6. Validate: move is legal, doesn't leave king in check
7. Return `(fromSquare, toSquare, promotionOption)` or `Left(error)`

**Supported SAN Patterns:**
- Pawn moves: e4, exd5, e8=Q, e8=Q+ (file+destination, capture, promotion, check)
- Piece moves: Nf3, Bxc6, Rd1, Qh5 (piece + destination)
- Disambiguation: Nbd2, N2d4, Ngf3 (file or rank or full square when 3+ same piece)
- Castling: O-O (kingside), O-O-O (queenside)
- Check/mate: Nf3+, Nf3# (suffix, ignored during expansion)

**Error Messages:**
- "Invalid SAN syntax: {san}"
- "No piece can make move {san}"
- "Move {san} is ambiguous (multiple pieces match)"
- "Move {san} leaves king in check"
- "Destination square is occupied by friendly piece in {san}"

**Notes:**
- Does NOT validate whether move results in check or checkmate (that's GameController's job)
- Greedy search means iterate over all legal moves once, no backtracking
- Castling O-O/O-O-O are expanded to (e1, g1) or (e1, c1) for White, (e8, g8) or (e8, c8) for Black

---

### 3. GUI: "Import PGN" Button
**Location:** `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala`

Add button to toolbar (after "Export PGN"):

```scala
private def buildImportPgnButton(): Button = ...
```

**Behavior on Click:**
1. Open FileChooser with PGN file filter (*.pgn)
2. User selects file
3. Read file as UTF-8 text
4. Call `Pgn.decode(text)` → extract headers and SAN move list
5. If decode fails → show error dialog with reason, stop
6. For each SAN move in sequence:
   - Get current GameController from GameManager
   - Call `SanDecoder.expand(board, san)` → get (from, to, promo)
   - If expand fails → show error dialog "Move N failed: {reason}", stop
   - Format algebraic: `"${from.toAlgebraic} ${to.toAlgebraic}"` + optional promo character
   - Call `GameManager.move(algebraic)` (or direct Board.move if outside GameManager)
   - If move fails → show error dialog "Move N illegal in position", stop
7. On success: final position displayed, player names from header shown in title or status label, game ready for undo/redo
8. Status label: "PGN imported: White vs Black" or error message on failure

**Error Dialog Format:**
```
Failed to load PGN

Move 15: {reason}
```

**Scoverage Exclusion:** Mark button building and file dialog code with `// $COVERAGE-OFF$` / `// $COVERAGE-ON$` to exclude from coverage.

---

## Data Flow

```
User clicks "Import PGN"
  ↓
FileChooser dialog
  ↓
User selects game.pgn
  ↓
Read file as String
  ↓
Pgn.decode(text)
  ├─ Parse [Tag "value"] lines → Map[String, String]
  ├─ Parse move list (space-separated SAN strings)
  └─ Return (headers, List[san1, san2, ...]) or Left(error)
  ↓
For each SAN move:
  ├─ Get current board from GameManager.state
  ├─ SanDecoder.expand(board, san) → (from, to, promo) or Left(error)
  ├─ Format algebraic string "a1 b2" + optional promo
  ├─ GameManager.move(algebraic)
  ├─ Observers (GUI, TUI) redraw on move
  └─ Continue to next move
  ↓
Final position reached → show board state + player names
```

**Error Halt:** Any error stops replay immediately. Game remains at last valid position (partial import).

---

## Error Handling

### Pgn.decode Errors (File-Level)
- File not found → show dialog: "Cannot read file: {path}"
- File encoding issues → show dialog: "File read error: invalid encoding"
- Malformed header → show dialog: "Invalid PGN format at line {N}: {reason}"
- Missing required tag → show dialog: "Missing PGN tag: {White|Black}"
- No move list → show dialog: "PGN has header but no moves"

### SanDecoder.expand Errors (Move-Level)
- Invalid SAN syntax → show dialog: "Move {N} has invalid syntax: {san}"
- No piece can make move → show dialog: "Move {N} is illegal: {san}"
- Ambiguous move (unresolved) → show dialog: "Move {N} is ambiguous: {san}"
- Leaves king in check → show dialog: "Move {N} is illegal: {san} (king in check)"

### Replay Errors (Game-Level)
- GameController.move returns error → show dialog: "Move {N} failed: {reason}"
- Board state inconsistency → show dialog: "Game ends at move {N}: {reason}"

### User Experience
- All errors are non-blocking (user can dismiss dialog, try again, or continue with current game)
- Error messages include move number and reason for easy debugging
- Partial imports are allowed (stop at first error, keep valid moves played)

---

## Testing

### PgnSpec.scala

**Header Parsing:**
- ✓ Valid PGN with all 7 tags → extracts Map with correct entries
- ✓ Valid PGN with 5 tags (missing 2) → extracts present tags, does not error
- ✓ Malformed header [Tag value] (no quotes) → Left with line number
- ✓ Duplicate tag [White "Alice"] [White "Bob"] → Left("Duplicate tag")
- ✓ Header line with extra spaces → still parses correctly

**Move Parsing:**
- ✓ Simple moves: "e4" → List("e4")
- ✓ Complex moves: "Nf3+ Nc6 Nxe5#" → List("Nf3+", "Nc6", "Nxe5#")
- ✓ Moves with numbers: "1. e4 1... e5 2. Nf3" → List("e4", "e5", "Nf3")
- ✓ Move list with result: "e4 e5 *" → List("e4", "e5"), result extracted separately
- ✓ Moves with comments: "e4 {best move} e5" → List("e4", "e5") (comments ignored)
- ✓ Moves with annotations: "e4! e5? Nf3!!" → List("e4", "e5", "Nf3") (annotations ignored)

**Edge Cases:**
- ✓ Empty header, empty moves → returns (empty map, empty list)
- ✓ No header, only moves → returns (empty map, list)
- ✓ Header with no moves → returns (map, empty list)
- ✓ Malformed move list (random text) → Left("Invalid SAN")

### SanDecoderSpec.scala

**Pawn Moves:**
- ✓ "e4" from initial position → (e2, e4, None)
- ✓ "e5" for Black → (e7, e5, None)
- ✓ "exd5" when pawn on e4 can capture d5 → (e4, d5, None)
- ✓ "axb5" when only a-pawn can capture → (a4, b5, None)

**Piece Moves:**
- ✓ "Nf3" from initial position → (g1, f3, None)
- ✓ "Bxc6" when Bishop can capture → (bishop square, c6, None)
- ✓ "Rd1" when Rook moves to d1 → (rook square, d1, None)
- ✓ "Qh5" when Queen moves to h5 → (queen square, h5, None)

**Disambiguation:**
- ✓ "Nbd2" when two knights can reach d2 → uses b-file knight
- ✓ "N2d4" when two knights can reach d4 → uses 2nd rank knight
- ✓ "Ngf3" when both knights can reach f3 → uses g-file knight
- ✓ "Nbxc6" full disambiguation (piece + file + capture) → correct knight

**Castling:**
- ✓ "O-O" from initial position → (e1, g1, None)
- ✓ "O-O-O" from initial position → (e1, c1, None)
- ✓ "O-O" for Black → (e8, g8, None)

**Promotion:**
- ✓ "e8=Q" from pawn on e7 → (e7, e8, Some(Queen))
- ✓ "e8=R" promotion to Rook → (e7, e8, Some(Rook))
- ✓ "exd8=Q+" pawn captures and promotes → (e7, d8, Some(Queen))

**Check/Mate Suffix:**
- ✓ "Nf3+" suffix ignored → same result as "Nf3"
- ✓ "Nf3#" checkmate suffix ignored → same result as "Nf3"

**Error Cases:**
- ✓ "Nf9" invalid destination → Left("Invalid square")
- ✓ "Nf3" when no knight can reach f3 → Left("No piece can make move")
- ✓ "Nxe4" when e4 is empty (invalid capture) → Left("No piece can make move")
- ✓ "Kh8" when King move leaves it in check → Left("Leaves king in check")
- ✓ "Nc7" ambiguous (3 knights to c7, unresolved) → Left("Ambiguous")

### GameManagerSpec.scala (Integration)

**Simple Games:**
- ✓ Import "1. e4 e5" → board matches expected 2-move position
- ✓ Import "1. e4 e5 2. Nf3" → Black to move, 3 half-moves in history

**Scholarly's Mate:**
- ✓ Import 4-move Fool's Mate → final board is checkmate position, result shown

**Promotion:**
- ✓ Import game with "e8=Q" → pawn on e8 is now Queen

**Castling:**
- ✓ Import game with "O-O" → King on g-file, Rook on f-file after castling

**Error Handling:**
- ✓ Invalid move in sequence → stops at move N, shows error, keeps previous valid moves
- ✓ Illegal move (e.g., moving opponent's piece in replay) → caught during replay

### Coverage Targets
- **Pgn.scala (decode):** ≥95% line, ≥90% branch
- **SanDecoder.scala (expand):** ≥95% line, ≥90% branch
- **ChessGUI.scala (import button):** Excluded via scoverage markers

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| **Modify** | `core/src/main/scala/de/eljachess/chess/model/Pgn.scala` | Add `decode` method |
| **Create** | `core/src/main/scala/de/eljachess/chess/controller/SanDecoder.scala` | SAN→algebraic expansion |
| **Modify** | `core/src/main/scala/de/eljachess/chess/gui/ChessGUI.scala` | Add "Import PGN" button |
| **Create** | `core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala` | Extend with decode tests |
| **Create** | `core/src/test/scala/de/eljachess/chess/controller/SanDecoderSpec.scala` | SanDecoder tests |
| **Modify** | `core/src/test/scala/de/eljachess/chess/controller/GameManagerSpec.scala` | Integration tests |
