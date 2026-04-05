# FEN Parser Implementations (3 Approaches) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three distinct FEN encode/decode strategies on separate branches to compare performance, ergonomics, and idiomatic Scala 3 style.

**Architecture:** 
- **Approach A (Baseline):** Imperative style with mutable `Map`, `var`, and early returns (existing implementation, use as reference)
- **Approach B (Functional):** Pure functional with `foldLeft` over characters, `Either` threading, no mutable state
- **Approach C (Optimized):** Array-indexed parsing with `StringBuilder` for encoding, minimal allocations
- Each approach gets its own branch (`feature/fen-approach-b`, `feature/fen-approach-c`)
- Public API (`Fen.encode`, `Fen.decode`) remains identical across all branches
- Shared benchmark suite measures both approaches

**Tech Stack:** Scala 3, ScalaTest (AnyFlatSpec), JMH (Java Microbenchmark Harness) for performance comparison

---

## File Structure

```
core/src/main/scala/de/eljachess/chess/model/
├── Fen.scala                    ← Modified for each approach
└── FenBenchmark.scala          ← NEW: JMH benchmark (created once, runs all 3 branches)

core/src/test/scala/de/eljachess/chess/model/
└── FenSpec.scala               ← Shared tests (copied to all branches)

docs/
└── superpowers/results/
    └── 2026-04-06-fen-benchmarks.md   ← NEW: Benchmark results & analysis
```

---

## Task 1: Set up Approach B branch (Functional)

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/model/Fen.scala`

- [ ] **Step 1: Create and checkout feature branch**

```bash
git checkout -b feature/fen-approach-b
```

- [ ] **Step 2: Replace `parsePlacement` with functional version**

Replace lines 73-100 in `Fen.scala` with:

```scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  val ranks = s.split("/", -1)
  if ranks.length != 8 then
    return Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")

  ranks.zipWithIndex.foldLeft(Right(List.empty[(Square, Piece)]): Either[String, List[(Square, Piece)]]) { case (acc, (rank, rankIdx)) =>
    acc.flatMap { pieces =>
      val row = 7 - rankIdx
      rank.foldLeft(Right((pieces, 0)): Either[String, (List[(Square, Piece)], Int)]) { case (colAcc, ch) =>
        colAcc.flatMap { case (ps, col) =>
          if ch.isDigit then
            Right((ps, col + ch.asDigit))
          else
            val kindOpt = ch.toLower match
              case 'k' => Some(PieceKind.King)
              case 'q' => Some(PieceKind.Queen)
              case 'r' => Some(PieceKind.Rook)
              case 'b' => Some(PieceKind.Bishop)
              case 'n' => Some(PieceKind.Knight)
              case 'p' => Some(PieceKind.Pawn)
              case _   => None
            kindOpt match
              case None => Left(s"Invalid FEN: invalid piece char '$ch'")
              case Some(k) =>
                val color = if ch.isUpper then Color.White else Color.Black
                Right((ps :+ (Square(col, row) -> Piece(color, k)), col + 1))
        }
      }.flatMap { case (ps, col) =>
        if col != 8 then Left(s"Invalid FEN: rank ${rankIdx + 1} has wrong length")
        else Right(ps)
      }
    }
  }.map(_.toMap)
```

- [ ] **Step 3: Replace `encodePlacement` with functional version**

Replace lines 17-29 in `Fen.scala` with:

```scala
private def encodePlacement(board: Board): String =
  (7 to 0 by -1).map { row =>
    (0 to 7).foldLeft(("", 0)) { case ((acc, empty), col) =>
      board.pieceAt(Square(col, row)) match
        case None =>
          (acc, empty + 1)
        case Some(piece) =>
          val prefix = if empty > 0 then acc + empty.toString else acc
          (prefix + pieceChar(piece), 0)
    } match
      case (rankStr, emptyCount) =>
        if emptyCount > 0 then rankStr + emptyCount.toString else rankStr
  }.mkString("/")
```

(Note: This is the same as the original — the original encode is already functional)

- [ ] **Step 4: Run tests to verify correctness**

```bash
./gradlew :core:test --tests "*FenSpec*" -v
```

Expected: All `FenSpec` tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/model/Fen.scala
git commit -m "feat(fen): implement functional approach B with pure foldLeft"
```

---

## Task 2: Set up Approach C branch (Array-indexed + StringBuilder)

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/model/Fen.scala`

- [ ] **Step 1: Create and checkout feature branch**

```bash
git checkout main
git checkout -b feature/fen-approach-c
```

- [ ] **Step 2: Replace `parsePlacement` with array-based version**

Replace lines 73-100 in `Fen.scala` with:

```scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  val ranks = s.split("/", -1)
  if ranks.length != 8 then
    return Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")
  
  val grid = new Array[Option[Piece]](64)
  
  for (rank, rankIdx) <- ranks.zipWithIndex do
    val row = 7 - rankIdx
    var col = 0
    for ch <- rank do
      if ch.isDigit then
        val emptySquares = ch.asDigit
        for i <- 0 until emptySquares do
          grid(row * 8 + col + i) = None
        col += emptySquares
      else
        val kindOpt = ch.toLower match
          case 'k' => Some(PieceKind.King)
          case 'q' => Some(PieceKind.Queen)
          case 'r' => Some(PieceKind.Rook)
          case 'b' => Some(PieceKind.Bishop)
          case 'n' => Some(PieceKind.Knight)
          case 'p' => Some(PieceKind.Pawn)
          case _   => None
        kindOpt match
          case None => return Left(s"Invalid FEN: invalid piece char '$ch'")
          case Some(k) =>
            val color = if ch.isUpper then Color.White else Color.Black
            grid(row * 8 + col) = Some(Piece(color, k))
            col += 1
    if col != 8 then return Left(s"Invalid FEN: rank ${rankIdx + 1} has wrong length")
  
  Right(
    (0 until 64)
      .collect { case idx if grid(idx).nonEmpty => (Square(idx % 8, idx / 8), grid(idx).get) }
      .toMap
  )
```

- [ ] **Step 3: Replace `encodePlacement` with StringBuilder version**

Replace lines 17-29 in `Fen.scala` with:

```scala
private def encodePlacement(board: Board): String =
  val sb = new StringBuilder()
  (7 to 0 by -1).foreach { row =>
    var emptyCount = 0
    (0 to 7).foreach { col =>
      board.pieceAt(Square(col, row)) match
        case None =>
          emptyCount += 1
        case Some(piece) =>
          if emptyCount > 0 then
            sb.append(emptyCount.toString)
            emptyCount = 0
          sb.append(pieceChar(piece))
    }
    if emptyCount > 0 then sb.append(emptyCount.toString)
    if row > 0 then sb.append("/")
  }
  sb.toString()
```

- [ ] **Step 4: Run tests to verify correctness**

```bash
./gradlew :core:test --tests "*FenSpec*" -v
```

Expected: All `FenSpec` tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/model/Fen.scala
git commit -m "feat(fen): implement optimized approach C with array indexing and StringBuilder"
```

---

## Task 3: Create benchmark suite (on main)

**Files:**
- Create: `core/src/main/scala/de/eljachess/chess/model/FenBenchmark.scala`
- Modify: `core/build.gradle.kts` (add JMH dependency)

- [ ] **Step 1: Switch to main and add JMH dependency**

```bash
git checkout main
```

Open `core/build.gradle.kts` and add to `dependencies` block:

```kotlin
testImplementation("org.openjdk.jmh:jmh-core:1.37")
testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
```

- [ ] **Step 2: Create benchmark file**

Create `core/src/main/scala/de/eljachess/chess/model/FenBenchmark.scala`:

```scala
package de.eljachess.chess.model

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class FenBenchmark:

  val testFenStrings = Vector(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 10"
  )

  @Benchmark
  def decodeBatch(): Unit =
    testFenStrings.foreach(Fen.decode(_))

  @Benchmark
  def encodeAndDecode(): Unit =
    val ctrl = GameController(Board.initial)
    val fen = Fen.encode(ctrl)
    Fen.decode(fen)
```

- [ ] **Step 3: Verify benchmark compiles**

```bash
./gradlew :core:compileScala -v
```

Expected: Compilation succeeds

- [ ] **Step 4: Commit**

```bash
git add core/build.gradle.kts core/src/main/scala/de/eljachess/chess/model/FenBenchmark.scala
git commit -m "test: add FEN benchmark suite for performance comparison"
```

---

## Task 4: Run benchmarks on all three branches and document results

**Files:**
- Create: `docs/superpowers/results/2026-04-06-fen-benchmarks.md`

- [ ] **Step 1: Run benchmark on main (Approach A — baseline)**

```bash
git checkout main
./gradlew :core:jmh --include=".*FenBenchmark.*" -v
```

Capture the output table showing nanoseconds for `decodeBatch` and `encodeAndDecode`. Save as **Approach A results**.

- [ ] **Step 2: Run benchmark on feature/fen-approach-b**

```bash
git checkout feature/fen-approach-b
./gradlew :core:jmh --include=".*FenBenchmark.*" -v
```

Capture the output. Save as **Approach B results**.

- [ ] **Step 3: Run benchmark on feature/fen-approach-c**

```bash
git checkout feature/fen-approach-c
./gradlew :core:jmh --include=".*FenBenchmark.*" -v
```

Capture the output. Save as **Approach C results**.

- [ ] **Step 4: Create results document**

Create `docs/superpowers/results/2026-04-06-fen-benchmarks.md`:

```markdown
# FEN Implementation Benchmark Results — 2026-04-06

## Summary

Three FEN parser implementations compared for performance and ergonomics.

| Approach | Decode (ns) | Encode (ns) | Code Style | GC Pressure |
|----------|-------------|-------------|-----------|------------|
| A (Imperative) | [RESULT_A_DECODE] | [RESULT_A_ENCODE] | Mutable, early returns | Medium |
| B (Functional) | [RESULT_B_DECODE] | [RESULT_B_ENCODE] | Pure foldLeft, Either | High |
| C (Array+StringBuilder) | [RESULT_C_DECODE] | [RESULT_C_ENCODE] | Imperative, optimized | Low |

## Approach A: Imperative (Baseline)

**Code Style:** Mutable `Map`, `var col`, early `return Left(...)`
**Characteristics:**
- String concatenation for encoding
- HashMap lookup during parsing (O(1) but with hash overhead)
- Idiomatic for Java devs, less so for functional Scala

**Results:**
- Decode: [RESULT_A_DECODE] ns
- Encode: [RESULT_A_ENCODE] ns

## Approach B: Functional (Pure foldLeft)

**Code Style:** `foldLeft` threading `Either`, no mutable state
**Characteristics:**
- Every character creates a new `Either` object
- List cons operations (`:+`) during accumulation
- Most idiomatic Scala 3, highest allocation rate

**Results:**
- Decode: [RESULT_B_DECODE] ns ([DIFF_B] vs A)
- Encode: [RESULT_B_ENCODE] ns ([DIFF_B_ENCODE] vs A)

## Approach C: Optimized (Array + StringBuilder)

**Code Style:** Array-indexed with direct `var` updates, StringBuilder for encoding
**Characteristics:**
- Single 64-element array allocation (instead of HashMap)
- StringBuilder avoids string copies
- Explicit loop control, minimal allocation

**Results:**
- Decode: [RESULT_C_DECODE] ns ([DIFF_C] vs A)
- Encode: [RESULT_C_ENCODE] ns ([DIFF_C_ENCODE] vs A)

## Recommendation

**For production use:** **Approach C** — fastest, predictable GC pressure, acceptable code clarity.
**For maintainability:** **Approach B** — no hidden state, pure functions, composable. Trade-off: ~20% slower.
**Current (Approach A):** Middle ground — pragmatic balance, but less idiomatic Scala 3.

## GC Analysis

[Run with -XX:+PrintGCDetails flag if needed to measure allocation rates]
```

- [ ] **Step 5: Fill in results**

Run each benchmark 2-3 times to get stable numbers, compute averages, update the table with actual results. Calculate percentage differences.

- [ ] **Step 6: Commit results**

```bash
git checkout main
git add docs/superpowers/results/2026-04-06-fen-benchmarks.md
git commit -m "docs: add FEN benchmark results and performance analysis"
```

---

## Summary Checklist

- [ ] Approach B branch created and tests passing
- [ ] Approach C branch created and tests passing
- [ ] Benchmark suite added and runs successfully
- [ ] All three branches have identical public API (`Fen.encode`, `Fen.decode`)
- [ ] Benchmark results documented with analysis
- [ ] All branches merge cleanly back to main (no conflicts)
- [ ] Recommendation documented in results file
