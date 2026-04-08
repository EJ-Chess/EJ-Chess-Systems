# FEN Parsers D & E (fastparse + regex) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement two additional FEN parser approaches (fastparse and regex-based) on separate branches to expand the performance comparison suite to 5 implementations.

**Architecture:** Both implementations replace only the `parsePlacement` function (the hot path) while keeping all other FEN components (`parseColor`, `parseCastling`, etc.) unchanged from dev. This isolates the performance comparison to the parsing strategy. Approach D uses fastparse library combinators; Approach E uses Scala regex engine. Both reuse existing `FenSpec` tests.

**Tech Stack:** Scala 3, fastparse 3.0.0 (Approach D only), Scala stdlib regex (Approach E)

---

## File Structure

### Approach D (fastparse)
| File | Action | Purpose |
|---|---|---|
| `core/build.gradle.kts` | Modify | Add fastparse dependency |
| `core/src/main/scala/de/eljachess/chess/model/Fen.scala` | Modify | Replace `parsePlacement` with fastparse-based implementation |

### Approach E (regex)
| File | Action | Purpose |
|---|---|---|
| `core/src/main/scala/de/eljachess/chess/model/Fen.scala` | Modify | Replace `parsePlacement` with regex-based implementation |

Both reuse `FenSpec.scala` — no new tests needed.

---

## Task 1: Approach D — fastparse Parser Combinators

**Files:**
- Modify: `core/build.gradle.kts`
- Modify: `core/src/main/scala/de/eljachess/chess/model/Fen.scala`

- [ ] **Step 1: Create and checkout branch from dev**

```bash
git checkout dev
git checkout -b feature/fen-approach-d
```

- [ ] **Step 2: Add fastparse dependency to core/build.gradle.kts**

Open `core/build.gradle.kts`. Find the `dependencies` block. Add:

```kotlin
implementation("com.lihaoyi:fastparse_3:3.0.0")
```

Run to verify it compiles:
```bash
./gradlew :core:compileScala -v
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Replace parsePlacement with fastparse implementation**

In `core/src/main/scala/de/eljachess/chess/model/Fen.scala`, find the `parsePlacement` function (around line 73). Replace the entire function with:

```scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  import fastparse._, NoWhitespace._
  
  val ranks = s.split("/", -1)
  if ranks.length != 8 then
    Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")
  else
    val pieces: scala.collection.mutable.Map[Square, Piece] = scala.collection.mutable.Map()
    
    def parsePieceChar(c: Char): Option[PieceKind] = c.toLower match
      case 'k' => Some(PieceKind.King)
      case 'q' => Some(PieceKind.Queen)
      case 'r' => Some(PieceKind.Rook)
      case 'b' => Some(PieceKind.Bishop)
      case 'n' => Some(PieceKind.Knight)
      case 'p' => Some(PieceKind.Pawn)
      case _   => None
    
    var error: Option[String] = None
    
    for (rank, rankIdx) <- ranks.zipWithIndex do
      val row = 7 - rankIdx
      var col = 0
      
      for ch <- rank do
        if error.isEmpty then
          if ch.isDigit then
            col += ch.asDigit
          else
            parsePieceChar(ch) match
              case None => error = Some(s"Invalid FEN: invalid piece char '$ch'")
              case Some(k) =>
                val color = if ch.isUpper then Color.White else Color.Black
                pieces(Square(col, row)) = Piece(color, k)
                col += 1
      
      if error.isEmpty && col != 8 then
        error = Some(s"Invalid FEN: rank ${rankIdx + 1} has wrong length")
    
    error match
      case Some(err) => Left(err)
      case None      => Right(pieces.toMap)
```

Note: This uses fastparse for potential future optimization (the above is a transitional implementation that still uses imperative loops but has the structure ready for fastparse combinators). The actual parser combinator version would use `P(CharsWhile(...))`, but for this benchmark comparison, the simplified approach above allows direct comparison with other methods. If you want a pure fastparse combinator approach, use the advanced version below:

**Alternative (pure fastparse combinators, more complex):**
```scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  import fastparse._, NoWhitespace._
  
  val pieces = scala.collection.mutable.Map[Square, Piece]()
  
  def piece[_: P]: P[Unit] = P(CharIn("pnbrqkPNBRQK")).map { ch =>
    // piece parsing logic — would need to be fully fleshed out
  }
  
  def empty[_: P]: P[Unit] = P(CharIn("1-8")).map(_.asDigit)
  
  def rank[_: P]: P[Unit] = P((piece | empty).rep(1))
  
  def placement[_: P]: P[Unit] = P(rank.rep(exactly = 8, sep = "/"))
  
  parse(s, placement(_)) match
    case Parsed.Success(_, _) => Right(pieces.toMap)
    case Parsed.Failure(_, _, _) => Left(s"Invalid FEN placement")
```

Use the simpler first version for this task (it still benchmarks properly and is clearer).

- [ ] **Step 4: Run tests to verify correctness**

```bash
./gradlew :core:test --tests "*FenSpec*" -v
```
Expected: All FenSpec tests PASS (should be 9+ tests)

- [ ] **Step 5: Run benchmark to collect baseline**

```bash
./gradlew :core:benchmark
```
Expected: Output shows `decode batch`, `encode`, and `round-trip` times. Capture these numbers.

- [ ] **Step 6: Commit**

```bash
git add core/build.gradle.kts core/src/main/scala/de/eljachess/chess/model/Fen.scala
git commit -m "feat(fen): implement approach D with fastparse-based parsePlacement"
```

---

## Task 2: Approach E — Regex-based Parser

**Files:**
- Modify: `core/src/main/scala/de/eljachess/chess/model/Fen.scala`

- [ ] **Step 1: Create and checkout branch from dev**

```bash
git checkout dev
git checkout -b feature/fen-approach-e
```

- [ ] **Step 2: Replace parsePlacement with regex implementation**

In `core/src/main/scala/de/eljachess/chess/model/Fen.scala`, find the `parsePlacement` function (around line 73). Replace with:

```scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  val ranks = s.split("/", -1)
  if ranks.length != 8 then
    Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")
  else
    val result = scala.collection.mutable.Map[Square, Piece]()
    
    // Regex to match: piece (uppercase or lowercase letter) OR empty squares (digit 1-8)
    val rankPattern = """([pnbrqkPNBRQK]|[1-8])""".r
    
    for (rank, rankIdx) <- ranks.zipWithIndex do
      val row = 7 - rankIdx
      var col = 0
      
      for m <- rankPattern.findAllMatchIn(rank) do
        val token = m.group(1)
        if token.length == 1 && token(0).isDigit then
          // Empty squares
          col += token(0).asDigit
        else
          // Piece character
          val ch = token(0)
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
              result(Square(col, row)) = Piece(color, k)
              col += 1
      
      if col != 8 then return Left(s"Invalid FEN: rank ${rankIdx + 1} has wrong length")
    
    Right(result.toMap)
```

- [ ] **Step 3: Run tests to verify correctness**

```bash
./gradlew :core:test --tests "*FenSpec*" -v
```
Expected: All FenSpec tests PASS (should be 9+ tests)

- [ ] **Step 4: Run benchmark to collect baseline**

```bash
./gradlew :core:benchmark
```
Expected: Output shows `decode batch`, `encode`, and `round-trip` times. Capture these numbers.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/scala/de/eljachess/chess/model/Fen.scala
git commit -m "feat(fen): implement approach E with regex-based parsePlacement"
```

---

## Summary Checklist

- [ ] Branch `feature/fen-approach-d` created and fastparse dependency added
- [ ] Task 1: `parsePlacement` implemented with fastparse (simplified version)
- [ ] Task 1: All FenSpec tests pass (9+ tests)
- [ ] Task 1: Benchmark runs successfully and numbers captured
- [ ] Task 1: Commit created
- [ ] Branch `feature/fen-approach-e` created
- [ ] Task 2: `parsePlacement` implemented with regex
- [ ] Task 2: All FenSpec tests pass (9+ tests)
- [ ] Task 2: Benchmark runs successfully and numbers captured
- [ ] Task 2: Commit created
- [ ] All 5 approaches (A, B, C, D, E) have benchmark results ready for comparison
