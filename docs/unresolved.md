# Unresolved Issues

## [2026-03-25] TUI default readLine closure not covered by tests

**Requirement / Bug:**
The default argument `() => scala.io.StdIn.readLine()` in `TUI.scala` is never
invoked by any unit test, because all tests inject a mock `readLine`. Scoverage
reports this closure as uncovered (invocation-count=0).

**Root Cause (if known):**
Intentional design: the default argument exists for production use only. Testing
it would require blocking on stdin or spawning a separate process, which is
out of scope for unit tests.

**Attempted Fixes:**
1. Deliberate exclusion — no fix attempted.

**Suggested Next Step:**
Accept as a known gap. Class-level coverage remains above the 95% threshold.
No action required unless a headless integration test suite is added.

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

## [2026-04-01] PGN Import: promotion replay not tested

**Requirement / Bug:**
Integration test for pawn promotion (e8=Q) cannot be verified because Board.move in this branch does not support promotion. SanDecoder.expand correctly returns Some(PieceKind.Queen) but the promotion is not applied to the board.

**Root Cause (if known):**
Promotion support was implemented in feature/pgn-export branch but not yet merged to this branch. Additionally, CommandParser.parse only accepts 2-token commands, so a 3-token "e7 e8 Q" command is rejected before reaching Board.move.

**Attempted Fixes:**
1. Replaced wrong Pawn-on-e8 assertion with a pending test stub.

**Suggested Next Step:**
Merge feature/pgn-export into this branch, then update the promotion integration test to assert PieceKind.Queen on e8.

## [2026-04-08] GameService: command-format bug causes all move operations to fail — RESOLVED

**Requirement / Bug:**
`GameService.makeMoveAlgebraic` and `GameService.makeMoveSan` both construct the
move command as `s"$from$to"` (e.g. `"e2e4"`) but `CommandParser.parse` requires
space-separated tokens (e.g. `"e2 e4"`). As a result, every move attempt returned
`Left("Invalid command format.")`.

**Root Cause:**
`GameService.scala`: all three command-building sites used concatenation without spaces,
and promotion suffix used `=Q` instead of the space-separated `Q` token.

**Resolution (2026-04-08):**
Fixed in `GameService.scala`:
- `makeMoveAlgebraic`: command changed to `s"$from $to$promoSuffix"`, promotion suffix to `s" ${pieceKindToChar(k)}"`
- `makeMoveSan`: command changed to `s"${from.toAlgebraic} ${to.toAlgebraic}$promoSuffix"`, same promotion fix
- `importPgn`: same command fix applied to the inline move application
All 38 `GameServiceSpec` tests pass after the fix.

## [2026-04-01] PGN Import: castling replay not tested

**Requirement / Bug:**
Integration test for O-O (kingside castling) cannot be verified because Board.move in this branch does not support castling. isValidKingMove rejects any king move where the column distance exceeds 1, so a king move from e1 to g1 (distance 2) is always rejected.

**Root Cause (if known):**
Castling support (two-square king move + rook teleport) has not been implemented in Board.move on this branch.

**Attempted Fixes:**
1. Added pending test stub "replay O-O and place king on g1 and rook on f1".

**Suggested Next Step:**
Implement castling in Board.move (detect dc == 2 for King, move king and also relocate the rook), then activate the pending test.
