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
