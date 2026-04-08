# FEN Parsers D & E (fastparse + regex) Design

**Date:** 2026-04-08  
**Branch:** dev (implementations on `feature/fen-approach-d` and `feature/fen-approach-e`)  

---

## Goal

Add two additional FEN parser implementations to expand the performance comparison suite from 3 approaches to 5:
- **Approach D — fastparse:** Parser combinator library, declarative approach
- **Approach E — regex-based:** Regex-driven parsing, alternative to hand-crafted code

All 5 implementations share the same `FenBenchmark` suite for direct comparison.

---

## Approach D — fastparse Parser Combinators

**What it is:**
fastparse is a Scala library for parsing combinator-based parsers. Instead of hand-writing imperative loops, you compose small parsers (`P` for "parser") that are chained together.

**Implementation scope:**
- **Only `parsePlacement`** is implemented with fastparse
- All other FEN components (`parseColor`, `parseCastling`, `parseEnPassant`, `parseHalfmove`, `parseFullmove`) remain unchanged from dev branch
- This isolates the performance comparison to the hot path (placement parsing)

**Key characteristics:**
- Declarative: "parse 8 ranks separated by /" rather than imperative loops
- External dependency: `com.lihaoyi:fastparse_3:3.0.0` (add to `core/build.gradle.kts`)
- Expected performance: Medium (combinator overhead, but well-optimized library)
- Code style: Very idiomatic Scala 3, highly readable

**Public API:**
- `def encode(ctrl: GameController): String` — unchanged
- `val decode: String => Either[String, GameController]` — signature unchanged, internal implementation uses fastparse for placement

---

## Approach E — regex-based Parsing

**What it is:**
Use Scala's built-in `scala.util.matching.Regex` to parse FEN placement strings. Similar philosophy to the hand-crafted imperative code, but delegating to the JVM's regex engine.

**Implementation scope:**
- **Only `parsePlacement`** uses regex patterns
- All other FEN components remain unchanged
- Two strategies:
  1. Single regex that captures the entire placement string and validates structure
  2. Iterative: one regex per rank, validate each rank individually
  - *Recommendation:* Iterative approach (easier to debug, matches the 8-rank structure of FEN)

**Key characteristics:**
- No external dependency (Scala stdlib only)
- Uses `Regex.findAllMatchIn` or similar to extract pieces/empty squares
- Expected performance: Slower than A/C (regex engine overhead), but interesting baseline
- Code style: Functional composition of regexes

**Public API:**
- `def encode(ctrl: GameController): String` — unchanged
- `val decode: String => Either[String, GameController]` — signature unchanged, internal implementation uses regex for placement

---

## Dependency Management

**Approach D only:**
Add to `core/build.gradle.kts` in the `dependencies` block:
```kotlin
implementation("com.lihaoyi:fastparse_3:3.0.0")
```

This is added on `feature/fen-approach-d` only. Approach E uses no new dependencies.

---

## Testing Strategy

Both implementations reuse the existing `FenSpec.scala` test suite. No new tests are written — the goal is to verify that all 5 approaches produce identical results.

- `FenSpec` runs on both branches and on main for comparison
- Expected: All existing 9+ FenSpec tests pass on all 5 branches
- Coverage: Tests already cover encode/decode round-trips, error cases, edge cases

---

## Benchmarking

The existing `FenBenchmark` on dev (created during the initial FEN comparison) is cherry-picked onto all 5 branches:
- `feature/fen-approach-a` (implied — dev main branch)
- `feature/fen-approach-b`
- `feature/fen-approach-c`
- `feature/fen-approach-d`
- `feature/fen-approach-e`

Running `./gradlew :core:benchmark` on each branch produces comparable numbers.

**Metrics to track:**
- `decodeBatch` (5 FENs): time per FEN string decoding
- `encode`: time per position encoding to FEN
- `round-trip`: time per encode + decode cycle

---

## Expected Performance Characteristics

| Approach | parsePlacement strategy | Expected vs Baseline |
|----------|------------------------|----------------------|
| A (dev) | Imperative + HashMap | Baseline |
| B | Functional foldLeft + Either | +25-30% (slower) |
| C | Array + StringBuilder | -15-20% (faster) |
| D | fastparse combinators | +5-15% (slight overhead) |
| E | regex engine | +20-30% (engine overhead) |

*These are estimates; actual results may differ.*

---

## Files Affected

### Branch `feature/fen-approach-d`
- Modify: `core/build.gradle.kts` (add fastparse dependency)
- Modify: `core/src/main/scala/de/eljachess/chess/model/Fen.scala` (only `parsePlacement`)
- No test changes (reuse FenSpec)

### Branch `feature/fen-approach-e`
- No dependency changes
- Modify: `core/src/main/scala/de/eljachess/chess/model/Fen.scala` (only `parsePlacement`)
- No test changes (reuse FenSpec)

---

## Success Criteria

1. Both `feature/fen-approach-d` and `feature/fen-approach-e` branches exist and are based on dev
2. `parsePlacement` is implemented using the specified strategy (fastparse / regex)
3. All existing FenSpec tests pass on both branches (9+ tests)
4. All 5 approaches (A, B, C, D, E) have stable benchmark numbers
5. Performance comparison document is updated with all 5 results

---

## Notes

- **Isolation:** Only `parsePlacement` changes. This keeps the comparison focused on parsing strategy.
- **Simplicity:** Approach E avoids external dependencies, making it easier to ship if needed.
- **Comparison:** The 5 implementations will provide a rich data set: imperative, functional, optimized, combinator-based, and regex-based.
