# FEN Implementation Comparison — Code vs Performance

## Overview

Three distinct FEN parser implementations compared across style, code patterns, and performance.

---

## Code Comparison

### `parsePlacement` — Decoding FEN placement string to piece positions

**Approach A — Imperative (Baseline)**
```scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  val ranks = s.split("/", -1)
  if ranks.length != 8 then
    return Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")
  val result = collection.mutable.Map[Square, Piece]()
  for (rank, rankIdx) <- ranks.zipWithIndex do
    val row = 7 - rankIdx
    var col = 0
    for ch <- rank do
      if ch.isDigit then
        col += ch.asDigit
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
          case None    => return Left(s"Invalid FEN: invalid piece char '$ch'")
          case Some(k) =>
            val color = if ch.isUpper then Color.White else Color.Black
            result(Square(col, row)) = Piece(color, k)
            col += 1
    if col != 8 then return Left(s"Invalid FEN: rank ${rankIdx + 1} has wrong length")
  Right(result.toMap)
```

**Key patterns:**
- Mutable `Map` accumulator (`collection.mutable.Map`)
- `var col` to track column position
- Early `return Left(...)` on error
- Direct HashMap insertion during parsing

---

**Approach B — Functional (Pure foldLeft)**
```scala
// Note: uses List.:+ (O(n) append) over the 64-element board — intentional trade-off for referential transparency
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  val ranks = s.split("/", -1)
  if ranks.length != 8 then
    Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")
  else
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

**Key patterns:**
- No mutable state — state threaded through `foldLeft`
- `Either` wrapping at every character (boxing overhead)
- `List.:+` accumulation (O(n) appends)
- No early returns — errors propagated via `flatMap`
- Immutable list converted to map at the end

---

**Approach C — Optimized (Array + StringBuilder)**
```scala
private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
  val ranks = s.split("/", -1)
  if ranks.length != 8 then
    return Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")

  val grid = Array.fill(64)(Option.empty[Piece])

  for (rank, rankIdx) <- ranks.zipWithIndex do
    val row = 7 - rankIdx
    var col = 0
    for ch <- rank do
      if ch.isDigit then
        col += ch.asDigit
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

**Key patterns:**
- Single `Array.fill(64)(Option.empty[Piece])` allocation
- Direct array indexing: `grid(row * 8 + col) = Some(...)`
- No HashMap during parsing — array is O(1) access
- Early returns (like Approach A)
- Post-processing: collect from array to map once

---

### `encodePlacement` — Encoding piece positions to FEN placement string

**Approach A — Imperative (String accumulation in foldLeft)**
```scala
private def encodePlacement(board: Board): String =
  (7 to 0 by -1).map { row =>
    val (rankStr, emptyCount) =
      (0 to 7).foldLeft(("", 0)) { case ((acc, empty), col) =>
        board.pieceAt(Square(col, row)) match
          case None =>
            (acc, empty + 1)
          case Some(piece) =>
            val prefix = if empty > 0 then acc + empty.toString else acc
            (prefix + pieceChar(piece), 0)
      }
    if emptyCount > 0 then rankStr + emptyCount.toString else rankStr
  }.mkString("/")
```

**Key patterns:**
- String concatenation: `acc + empty.toString` and `prefix + pieceChar(piece)`
- Accumulator tuple `(String, Int)` threaded through foldLeft
- No StringBuilder — strings are immutable and each `+` creates a new object

---

**Approach B — Identical to Approach A**

Approach B uses the exact same `encodePlacement` as Approach A because the encode path was already idiomatic functional code.

---

**Approach C — Optimized (StringBuilder)**
```scala
private def encodePlacement(board: Board): String =
  val sb = new StringBuilder(80)
  (7 to 0 by -1).foreach { row =>
    if row < 7 then sb.append('/')
    var emptyCount = 0
    (0 to 7).foreach { col =>
      board.pieceAt(Square(col, row)) match
        case None =>
          emptyCount += 1
        case Some(piece) =>
          if emptyCount > 0 then
            sb.append(emptyCount)
            emptyCount = 0
          sb.append(pieceChar(piece))
    }
    if emptyCount > 0 then sb.append(emptyCount)
  }
  sb.toString()
```

**Key patterns:**
- Single `StringBuilder` (pre-allocated 80 chars)
- `sb.append(Char)` / `sb.append(Int)` — no intermediate string objects
- Rank separators prepended: `if row < 7 then sb.append('/')`
- No string concatenation in the loop

---

## Performance Results

Benchmark: 50,000 iterations, 5,000 warmup iterations

| Operation | Approach A (Imperative) | Approach B (Functional) | Approach C (Optimized) |
|-----------|------------------------|------------------------|------------------------|
| **decode batch (5 FENs)** | 19,306 ns/op | 25,286 ns/op (**+31%**) | 16,581 ns/op (**-14%**) |
| **encode (initial board)** | 6,911 ns/op | 6,400 ns/op (-7%) | 5,504 ns/op (**-20%**) |
| **round-trip** | 9,347 ns/op | 10,799 ns/op (+16%) | 7,621 ns/op (**-18%**) |

---

## Key Differences Explained

### **Decode (parsePlacement)**

| Factor | A | B | C |
|--------|---|---|---|
| **Data structure** | Mutable HashMap | Immutable List | Array[Option] |
| **Per-character cost** | 1 HashMap insert | 1 Either allocation + List cons | 1 array write |
| **Allocation pattern** | Map grows as pieces added | 64 new tuples + Either objects | Single 64-element array |
| **Why A vs B:** B wraps every character in `Either` and does `:+` list appends, creating intermediate objects that A's HashMap avoids | | |
| **Why A vs C:** C uses array (O(1) indexed) instead of HashMap (O(1) hashing + hash collision handling). Array has no hash computation overhead | | |

**Winner:** C — no hash computation, pre-allocated array, direct indexing

---

### **Encode (encodePlacement)**

| Factor | A | B | C |
|--------|---|---|---|
| **String building** | String concat (`+`) | String concat (`+`) | StringBuilder |
| **Allocations** | ~64 intermediate strings | ~64 intermediate strings | 1 StringBuilder, pre-sized |
| **Per-character cost** | 1 string copy | 1 string copy | 1 append operation |

**Winner:** C — StringBuilder avoids string copy overhead, pre-sized allocation eliminates resizing

---

### **Code Style**

| Aspect | A | B | C |
|--------|---|---|---|
| **Mutability** | Mutable (`var`, `collection.mutable.Map`) | Pure (no `var`, no mutable) | Mutable (`var col`, array) |
| **Early returns** | Yes | No | Yes |
| **Idiomatic Scala 3** | ⭐⭐ (pragmatic) | ⭐⭐⭐⭐⭐ (pure FP) | ⭐⭐⭐ (imperative optimized) |
| **Readability** | High (imperative is familiar) | Medium (nested foldLeft is dense) | High (straightforward imperative) |
| **Composability** | Low (early returns, mutable) | High (pure functions) | Low (mutable state) |

---

## Which One to Choose?

### **For Production:** **Approach C**
- Fastest across all benchmarks
- Predictable GC behavior (single array allocation)
- Clear, straightforward code that's easy to maintain
- No overhead from hashing or functional boxing

### **For Code Style / Teaching:** **Approach B**
- Most idiomatic Scala 3 (functional, composable, no mutable state)
- Exemplifies pure functional design
- Trade-off: ~30% slower on decode due to Either boxing and List appends
- Best for a functional programming course or style guide

### **Current (Approach A):** **Pragmatic Middle Ground**
- Reasonable performance (between B and C)
- Familiar imperative style
- Less idiomatic than B, slower than C
- Existing implementation — migration cost vs. benefit

---

## Benchmark Notes

- Tested on Windows 11, Java 25, Scala 3.5.1
- JIT warmup (5,000 iterations) before measurement (50,000 iterations)
- FEN strings: initial position + 4 complex mid-game positions
- `ns/op` = nanoseconds per operation (lower is better)

The performance gap is real but small in absolute terms (microseconds). For a chess GUI handling one move per second, Approach B's +31% decode time (6 µs slower per position) is imperceptible. Approach C's -14% (3 µs faster) is also imperceptible. **The choice should be based on your priorities: performance, code style, or maintainability.**

---

## Summary Table

```
┌──────────────────┬─────────────────┬─────────────────┬─────────────────┐
│ Criterion        │ Approach A       │ Approach B       │ Approach C       │
├──────────────────┼─────────────────┼─────────────────┼─────────────────┤
│ Speed (decode)   │ 19,306 ns/op    │ 25,286 ns/op +31│ 16,581 ns/op -14│
│ Speed (encode)   │ 6,911 ns/op     │ 6,400 ns/op -7% │ 5,504 ns/op -20│
│ Style            │ Imperative      │ Functional      │ Imperative opt. │
│ Mutability       │ Mutable Map/var │ Pure            │ Mutable array   │
│ Idiomatic Scala3 │ ⭐⭐            │ ⭐⭐⭐⭐⭐       │ ⭐⭐⭐           │
│ Production ready │ ✓ (current)     │ ✓ (style over   │ ✓ (perf)        │
│                  │                 │ perf)           │                 │
└──────────────────┴─────────────────┴─────────────────┴─────────────────┘
```
