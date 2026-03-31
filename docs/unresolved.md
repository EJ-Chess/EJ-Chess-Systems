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

## [2026-03-31] PGN SAN disambiguation — FIDE case 3 (3+ same-kind pieces)

**Requirement / Bug:**
When three or more pieces of the same kind and color can all reach the same destination square, FIDE disambiguation requires the full square (file + rank) in SAN. The current implementation in `Pgn.sanForPieceMove` only emits one character (file or rank) in this case.

**Root Cause:**
`Pgn.scala` lines ~79-83: the disambiguation logic handles two-piece disambiguation (FIDE cases 1 and 2) but silently falls through to a one-character disambiguator for the three-piece case.

**Attempted Fixes:**
None — deferred as an edge case requiring three or more promoted pieces.

**Suggested Next Step:**
After counting all other pieces of the same kind/color that can reach `to`, if more than one share both file and rank, emit `from.toAlgebraic` (full square) instead of one character.
