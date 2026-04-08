# FEN Approach F: Parser Combinators (cats-parse) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a pure parser combinator approach to FEN placement parsing using cats-parse, benchmark it against existing approaches, and update the comparison document.

**Architecture:** Break FEN placement syntax into composable parser primitives (digit parser, piece parser) and combine them into a complete rank and placement parser. All parsing flows through Either[ParsingError, Result] without mutable state.

**Tech Stack:** 
- **Library:** org.typelevel:cats-parse_3:1.0.0
- **Testing:** ScalaTest (AnyFlatSpec with Matchers)
- **Benchmarking:** Existing FenBenchmark.scala macro

---

## File Structure

### New Files
- **Create:** `core/src/main/scala/de/eljachess/chess/model/ParserCombinatorsFEN.scala`
  - Pure combinator-based `parsePlacement` implementation
  - Helper parsers: `digitParser`, `pieceParser`, `rankParser`, `placementParser`
  - Full error messages for parse failures

### Modified Files
- **Modify:** `core/build.gradle.kts`
  - Add cats-parse dependency to `dependencies` block

- **Modify:** `core/src/test/scala/de/eljachess/chess/model/FenSpec.scala`
  - Add test cases specifically for ParserCombinatorsFEN (in a separate describe block)

- **Modify:** `docs/superpowers/results/2026-04-07-fen-implementation-comparison.md`
  - Add Approach F section with code examples
  - Add F column to performance table
  - Update key differences table
  - Add F to final recommendation summary

---

## Task 1: Add cats-parse Dependency

**Files:**
- Modify: `core/build.gradle.kts:dependencies`

- [ ] **Step 1: Read current build.gradle.kts**

Check the dependencies block to understand the version catalog pattern used in this project.

- [ ] **Step 2: Add cats-parse to build.gradle.kts**

Locate the `dependencies { }` block in `core/build.gradle.kts` and add:

```kotlin
implementation("org.typelevel:cats-parse_3:1.0.0")
```

Insert this line with the other `implementation(...)` entries (after scalafx, before test dependencies).

- [ ] **Step 3: Verify syntax**

Run a quick syntax check:
```bash
./gradlew build --dry-run
```

Expected output: No errors, build plan shows project is recognized.

- [ ] **Step 4: Commit dependency**

```bash
git add core/build.gradle.kts
git commit -m "feat(fen): add cats-parse dependency for approach F"
```

---

## Task 2: Implement ParserCombinatorsFEN with Pure Combinators

**Files:**
- Create: `core/src/main/scala/de/eljachess/chess/model/ParserCombinatorsFEN.scala`

- [ ] **Step 1: Create new file with imports and object declaration**

Create `core/src/main/scala/de/eljachess/chess/model/ParserCombinatorsFEN.scala`:

```scala
// core/src/main/scala/de/eljachess/chess/model/ParserCombinatorsFEN.scala
package de.eljachess.chess.model

import cats.parse.{Parser, Parser0, Numbers, Rfc5234}
import scala.collection.mutable

object ParserCombinatorsFEN:

  /**
   * Parse FEN placement string using pure parser combinators.
   * Example: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
   */
  def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
    placementParser.parseAll(s) match
      case Right(pieces) => Right(pieces.toMap)
      case Left(error)   => Left(s"Invalid FEN: ${error.toString()}")
```

- [ ] **Step 2: Add atomic parsers for digits and pieces**

Add these parser definitions inside the object:

```scala
  /**
   * Parser for a single piece character: [pnbrqkPNBRQK]
   * Returns (color, pieceKind)
   */
  private val pieceParser: Parser[(Color, PieceKind)] =
    val whitePieces = Parser.charIn("KQRBNP").map { ch =>
      (Color.White, ch.toLower match
        case 'k' => PieceKind.King
        case 'q' => PieceKind.Queen
        case 'r' => PieceKind.Rook
        case 'b' => PieceKind.Bishop
        case 'n' => PieceKind.Knight
        case 'p' => PieceKind.Pawn)
    }
    val blackPieces = Parser.charIn("kqrbnp").map { ch =>
      (Color.Black, ch match
        case 'k' => PieceKind.King
        case 'q' => PieceKind.Queen
        case 'r' => PieceKind.Rook
        case 'b' => PieceKind.Bishop
        case 'n' => PieceKind.Knight
        case 'p' => PieceKind.Pawn)
    }
    whitePieces | blackPieces

  /**
   * Parser for empty squares: [1-8]
   * Returns the number of empty squares.
   */
  private val digitParser: Parser[Int] =
    Parser.charIn('1' to '8').map(_.asDigit)

  /**
   * A rank token: either a piece or a digit (empty count).
   */
  private val rankTokenParser: Parser[Either[Int, (Color, PieceKind)]] =
    (digitParser.map(Left(_))) | (pieceParser.map(Right(_)))

  /**
   * A complete rank string (e.g., "rnbqkbnr").
   * Returns list of (col, color, pieceKind) tuples where col is 0..7.
   */
  private val rankParser: Parser[List[(Int, Color, PieceKind)]] =
    rankTokenParser.rep.map { tokens =>
      val pieces = mutable.ListBuffer[(Int, Color, PieceKind)]()
      var col = 0
      for token <- tokens do
        token match
          case Left(emptyCount) =>
            col += emptyCount
          case Right((color, kind)) =>
            if col >= 8 then
              sys.error(s"Rank exceeds 8 squares (col=$col)")
            pieces += ((col, color, kind))
            col += 1
      if col != 8 then
        sys.error(s"Rank has ${col} squares, expected 8")
      pieces.toList
    }

  /**
   * Parser for 8 ranks separated by "/", yielding all pieces with their squares.
   * Tracks row numbering (rank 8 → row 7, rank 1 → row 0).
   */
  private val placementParser: Parser[List[(Square, Piece)]] =
    val rankSeparator = Parser.char('/')
    (rankParser.repExactly(8, sep = rankSeparator)).map { ranks =>
      val pieces = mutable.ListBuffer[(Square, Piece)]()
      for (rank, rankIdx) <- ranks.zipWithIndex do
        val row = 7 - rankIdx  // rank 8 (index 0) → row 7, rank 1 (index 7) → row 0
        for (col, color, kind) <- rank do
          pieces += ((Square(col, row) -> Piece(color, kind)))
      pieces.toList
    }
```

- [ ] **Step 3: Build and test basic compilation**

```bash
./gradlew :core:compileScala
```

Expected: Compilation succeeds (no undefined parsers or imports).

- [ ] **Step 4: Commit parser implementation**

```bash
git add core/src/main/scala/de/eljachess/chess/model/ParserCombinatorsFEN.scala
git commit -m "feat(fen): add ParserCombinatorsFEN with pure cats-parse combinators"
```

---

## Task 3: Write Tests for ParserCombinatorsFEN

**Files:**
- Modify: `core/src/test/scala/de/eljachess/chess/model/FenSpec.scala`

- [ ] **Step 1: Read existing FenSpec to understand test structure**

Review `FenSpec.scala` to understand the test pattern and existing test cases.

- [ ] **Step 2: Add test block for ParserCombinatorsFEN**

Append a new describe block to FenSpec.scala (before the final closing brace):

```scala
  describe("ParserCombinatorsFEN.parsePlacement") {

    it("should parse initial position") {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      val expected = Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        .map(_.board.pieces)
        .getOrElse(Map())
      result should be(Right(expected))
    }

    it("should parse empty board (all 8s)") {
      val fen = "8/8/8/8/8/8/8/8"
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      result should be(Right(Map()))
    }

    it("should parse mixed rank with pieces and empty squares") {
      val fen = "r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R"
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      result.isRight should be(true)
      // Verify at least some pieces are present
      result.map(_.size >= 24) should be(Right(true))
    }

    it("should reject placement with wrong rank count") {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP"  // only 7 ranks
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      result.isLeft should be(true)
    }

    it("should reject rank with invalid characters") {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPXPPP/RNBQKBNR"  // 'X' is invalid
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      result.isLeft should be(true)
    }

    it("should reject rank with wrong square count") {
      val fen = "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR"  // 9 squares in rank 3
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      result.isLeft should be(true)
    }

    it("should parse rank 4 mid-game position") {
      val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8"
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      result.isRight should be(true)
    }

    it("should parse position with all piece types") {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
      val result = ParserCombinatorsFEN.parsePlacement(fen)
      result.isRight should be(true)
      result.map(_.size) should be(Right(32))
    }

  }
```

- [ ] **Step 3: Run tests to verify they pass**

```bash
./gradlew :core:test --tests "*FenSpec*ParserCombinatorsFEN*"
```

Expected: All 8 tests pass (✓).

- [ ] **Step 4: Run full test suite for FenSpec**

```bash
./gradlew :core:test --tests "*FenSpec*"
```

Expected: All FenSpec tests pass (no regressions).

- [ ] **Step 5: Commit tests**

```bash
git add core/src/test/scala/de/eljachess/chess/model/FenSpec.scala
git commit -m "test(fen): add comprehensive tests for ParserCombinatorsFEN"
```

---

## Task 4: Integrate ParserCombinatorsFEN into Benchmarks

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/model/FenBenchmark.scala`

- [ ] **Step 1: Read FenBenchmark.scala**

Review the current structure to understand how to add a new approach.

- [ ] **Step 2: Add ParserCombinatorsFEN benchmark**

In the `run()` method, after the existing benchmarks, add:

```scala
    println()
    println("=== ParserCombinatorsFEN (Approach F) ===")
    println()

    bench("decode batch (5 FENs) - ParserCombinatorsFEN", warmup = 5_000, iters = 50_000) {
      testFens.foreach(ParserCombinatorsFEN.parsePlacement)
    }
```

Insert this before the final `println("Done.")` line.

- [ ] **Step 3: Build and run benchmark**

```bash
./gradlew :core:benchmark
```

Expected output: Benchmark runs successfully and prints timing for ParserCombinatorsFEN approach. Record the `ns/op` value for the decode batch operation.

Example expected output:
```
decode batch (5 FENs) - ParserCombinatorsFEN  XXXXX.X ns/op  (50000 iters)
```

- [ ] **Step 4: Document benchmark result**

Note the result (e.g., "26,500 ns/op") — you'll need this for the comparison document.

- [ ] **Step 5: Commit benchmark integration**

```bash
git add core/src/main/scala/de/eljachess/chess/model/FenBenchmark.scala
git commit -m "bench(fen): add ParserCombinatorsFEN timing to benchmark"
```

---

## Task 5: Update Comparison Document with Approach F

**Files:**
- Modify: `docs/superpowers/results/2026-04-07-fen-implementation-comparison.md`

- [ ] **Step 1: Add Approach F code section**

After the "Approach E — Regex-based Parser" section, add:

```markdown
---

**Approach F — cats-parse Parser Combinators**

\`\`\`scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  placementParser.parseAll(s) match
    case Right(pieces) => Right(pieces.toMap)
    case Left(error)   => Left(s"Invalid FEN: ${error.toString()}")

private val pieceParser: Parser[(Color, PieceKind)] =
  val whitePieces = Parser.charIn("KQRBNP").map { ch =>
    (Color.White, ch.toLower match
      case 'k' => PieceKind.King
      case 'q' => PieceKind.Queen
      case 'r' => PieceKind.Rook
      case 'b' => PieceKind.Bishop
      case 'n' => PieceKind.Knight
      case 'p' => PieceKind.Pawn)
  }
  val blackPieces = Parser.charIn("kqrbnp").map { ch =>
    (Color.Black, ch match
      case 'k' => PieceKind.King
      case 'q' => PieceKind.Queen
      case 'r' => PieceKind.Rook
      case 'b' => PieceKind.Bishop
      case 'n' => PieceKind.Knight
      case 'p' => PieceKind.Pawn)
  }
  whitePieces | blackPieces

private val digitParser: Parser[Int] =
  Parser.charIn('1' to '8').map(_.asDigit)

private val rankTokenParser: Parser[Either[Int, (Color, PieceKind)]] =
  (digitParser.map(Left(_))) | (pieceParser.map(Right(_)))

private val rankParser: Parser[List[(Int, Color, PieceKind)]] =
  rankTokenParser.rep.map { tokens =>
    val pieces = mutable.ListBuffer[(Int, Color, PieceKind)]()
    var col = 0
    for token <- tokens do
      token match
        case Left(emptyCount) =>
          col += emptyCount
        case Right((color, kind)) =>
          if col >= 8 then sys.error(s"Rank exceeds 8 squares")
          pieces += ((col, color, kind))
          col += 1
    if col != 8 then sys.error(s"Rank has ${col} squares, expected 8")
    pieces.toList
  }

private val placementParser: Parser[List[(Square, Piece)]] =
  val rankSeparator = Parser.char('/')
  (rankParser.repExactly(8, sep = rankSeparator)).map { ranks =>
    val pieces = mutable.ListBuffer[(Square, Piece)]()
    for (rank, rankIdx) <- ranks.zipWithIndex do
      val row = 7 - rankIdx
      for (col, color, kind) <- rank do
        pieces += ((Square(col, row) -> Piece(color, kind)))
    pieces.toList
  }
\`\`\`

**Key patterns:**
- Composable parser primitives: `pieceParser`, `digitParser`, `rankTokenParser`
- Parser composition: `rankParser` combines tokens, `placementParser` combines ranks
- No mutable state in top-level `parsePlacement` (mutable accumulation inside combinator)
- Error propagation through `Either` at parse level
- Dependency: `org.typelevel:cats-parse_3:1.0.0`
```

- [ ] **Step 2: Add row to performance results table**

Find the performance table (around line 325-333). Update it to include Approach F:

```markdown
| Operation | A (Imperative) | B (Functional) | C (Optimized) | D (fastparse) | E (Regex) | F (cats-parse) |
|-----------|---|---|---|---|---|---|
| **decode batch (5 FENs)** | 19,306 ns/op | 25,286 ns/op (+31%) | 16,581 ns/op (-14%) | 18,384.6 ns/op (-5%) | 35,626.0 ns/op (+85%) | [YOUR_RESULT] ns/op |
| **encode (initial board)** | 6,911 ns/op | 6,400 ns/op (-7%) | 5,504 ns/op (-20%) | 7,121.5 ns/op (+3%) | 7,292.2 ns/op (+6%) | (N/A - not benchmarked) |
| **round-trip** | 9,347 ns/op | 10,799 ns/op (+16%) | 7,621 ns/op (-18%) | 9,110.1 ns/op (-2%) | 12,258.2 ns/op (+31%) | (N/A - not benchmarked) |
```

Replace `[YOUR_RESULT]` with the actual ns/op value from Task 4.

- [ ] **Step 3: Add F to the Key Differences table**

Find the table under "### **Decode (parsePlacement)**" (around line 340-350). Add a new column for F:

```markdown
| Factor | A | B | C | D | E | F |
|--------|---|---|---|---|---|---|
| **Data structure** | Mutable HashMap | Immutable List | Array[Option] | Mutable Map | Mutable Map | Parser combinator result |
| **Parsing method** | Manual char loop | foldLeft chains | Manual char loop | Manual loops | Regex findAllMatchIn | cats-parse combinators |
| **Per-character cost** | 1 HashMap insert | 1 Either + List cons | 1 array write | 1 HashMap insert | Regex pattern match | Parser composition |
| **Why F vs A:** F uses high-level parser combinators, adding abstraction overhead but eliminating manual state tracking. Trade-off: readability + composability vs. raw speed | | | | | |
```

- [ ] **Step 4: Update style comparison table**

Find the "### **Code Style**" section table (around line 370-380). Add F column:

```markdown
| Aspect | A | B | C | D | E | F |
|--------|---|---|---|---|---|---|
| **Mutability** | Mutable (`var`, `Map`) | Pure (no `var`, no mutable) | Mutable (`var col`, array) | Mutable (`var`, `Map`) | Mutable (`var col`, `Map`) | Pure (combinators are immutable, internal mutable-only for accumulation) |
| **Early returns** | Yes | No | Yes | Yes | Yes | No (parser chain, error via Left) |
| **External deps** | None | None | None | fastparse 3.0.0 | None (stdlib regex) | cats-parse 1.0.0 |
| **Idiomatic Scala 3** | ⭐⭐ (pragmatic) | ⭐⭐⭐⭐⭐ (pure FP) | ⭐⭐⭐ (imperative opt.) | ⭐⭐⭐ (library-ready) | ⭐⭐ (regex-heavy) | ⭐⭐⭐⭐⭐ (pure FP + composable) |
| **Readability** | High (imperative) | Medium (dense foldLeft) | High (straightforward) | Medium (mixed) | Medium (regex patterns) | Medium-High (requires parser knowledge) |
| **Composability** | Low (early returns) | High (pure functions) | Low (mutable state) | Low (early returns) | Low (early returns) | Very High (by design) |
```

- [ ] **Step 5: Add F to summary table (end of document)**

Find the final summary table (around line 435-450). Add F column and update with benchmark results:

```markdown
│ Decode speed     │ 19,306 ns/op    │ 25,286 +31%     │ 16,581 -14%     │ 18,384 -5%       │ 35,626 +85% ❌   │ [YOUR_PCT]%      │
```

Update all references to include Approach F in the summary.

- [ ] **Step 6: Add F to final recommendation section**

Find the "## Which One to Choose?" section (around line 380-410). Add subsection for Approach F:

```markdown
### **For Code Elegance / Advanced FP:** **Approach F (cats-parse)**
- **Most composable** — parsers are first-class values, easy to refactor and extend
- **Pure functional design** — combinators are immutable, no early returns
- **Idiomatic Scala 3 + FP** — ⭐⭐⭐⭐⭐ for code style
- **Performance:** TBD (depends on benchmark results) — likely slower than A/C/D due to abstraction overhead
- **Best for:** Code that needs future extension (e.g., multi-format parsing), teaching, or teams that prioritize composability
- **Tradeoff:** Adds cats-parse dependency, may be overkill for static FEN parsing
```

- [ ] **Step 7: Verify document renders correctly**

Open the markdown file in your editor and check for:
- No broken links or references
- Tables are properly aligned
- Code blocks have correct syntax highlighting (```scala)
- All 6 approaches (A-F) are represented consistently

- [ ] **Step 8: Commit comparison document update**

```bash
git add docs/superpowers/results/2026-04-07-fen-implementation-comparison.md
git commit -m "docs(fen): add Approach F (cats-parse) to comparison"
```

---

## Task 6: Create Feature Branch and Final Verification

**Files:**
- None (branch management only)

- [ ] **Step 1: Create new branch if not already on one**

Check current branch:
```bash
git branch --show-current
```

If not on `feature/fen-approach-f-parser-combinators`, create and switch:
```bash
git checkout -b feature/fen-approach-f-parser-combinators
```

- [ ] **Step 2: Run full test suite**

```bash
./gradlew :core:test
```

Expected: All tests pass (including new ParserCombinatorsFEN tests).

- [ ] **Step 3: Run code coverage check**

```bash
./gradlew :core:test :core:reportScoverage
```

Expected: Coverage reports are generated. Check that FenSpec (and new ParserCombinatorsFEN tests) have ≥95% line coverage.

- [ ] **Step 4: Verify benchmark runs without errors**

```bash
./gradlew :core:benchmark
```

Expected: Benchmark completes successfully, ParserCombinatorsFEN timing is printed.

- [ ] **Step 5: Review git log**

```bash
git log --oneline -10
```

Expected: See 5 commits related to this approach:
1. "feat(fen): add cats-parse dependency for approach F"
2. "feat(fen): add ParserCombinatorsFEN with pure cats-parse combinators"
3. "test(fen): add comprehensive tests for ParserCombinatorsFEN"
4. "bench(fen): add ParserCombinatorsFEN timing to benchmark"
5. "docs(fen): add Approach F (cats-parse) to comparison"

- [ ] **Step 6: Final verification**

Confirm all files are committed:
```bash
git status
```

Expected: "nothing to commit, working tree clean"

- [ ] **Step 7: Log final benchmark results**

Create a brief summary in your session notes:
- Benchmark result (ns/op) for ParserCombinatorsFEN decode
- Percentage comparison to Approach C (optimal)
- Whether it met design goals (no specific target was set)

---

## Summary

After completing these 6 tasks:

✅ **Dependency:** cats-parse added to build.gradle.kts  
✅ **Implementation:** Pure combinator-based parsePlacement in ParserCombinatorsFEN.scala  
✅ **Tests:** 8 test cases for ParserCombinatorsFEN, all passing  
✅ **Benchmarks:** Timing data collected and recorded  
✅ **Documentation:** Comparison document updated with Approach F code and analysis  
✅ **Branch:** Feature branch `feature/fen-approach-f-parser-combinators` ready for review  

**Next steps after completion:**
- Optionally: PR to main branch if approved
- Compare results with other approaches
- Consider whether to keep Approach F in production or as reference implementation
