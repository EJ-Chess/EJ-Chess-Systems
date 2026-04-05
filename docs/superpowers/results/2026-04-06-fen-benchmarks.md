# FEN Implementation Benchmark Results — 2026-04-06

## Environment

- JVM: Java 25.0.1 (Java HotSpot(TM) 64-Bit Server VM, build 25.0.1+8-LTS-27, mixed mode, sharing)
- Scala: 3.5.1
- Iterations: 50 000 measurement + 5 000 warmup
- Machine: Windows 11

## Results

### decode batch (5 FENs per iteration)

| Approach | ns/op | vs Baseline |
|----------|-------|-------------|
| A — Imperative (mutable Map + var) | 19 305.8 | baseline |
| B — Functional (foldLeft + flatMap) | 25 285.9 | +31% |
| C — Optimised (Array.fill + StringBuilder) | 16 580.7 | -14% |

### encode (initial board)

| Approach | ns/op | vs Baseline |
|----------|-------|-------------|
| A — Imperative | 6 911.2 | baseline |
| B — Functional | 6 399.6 | -7% |
| C — Optimised | 5 503.8 | -20% |

### round-trip (encode + decode)

| Approach | ns/op | vs Baseline |
|----------|-------|-------------|
| A — Imperative | 9 346.6 | baseline |
| B — Functional | 10 798.6 | +16% |
| C — Optimised | 7 620.7 | -18% |

## Analysis

### Decode

Approach B is 31% slower than the imperative baseline because its pure `foldLeft` over the FEN string allocates a new immutable state tuple on every character, and `Either`-wrapping error paths adds boxing overhead on the hot path. Approach A's mutable `Map` updates and `var col` counter avoid that allocation pressure entirely, and early returns skip remaining work as soon as a parse error is detected. Approach C beats both because it pre-allocates a single `Array.fill(64)` and indexes directly into it by (row * 8 + col), eliminating all intermediate collection construction. The net result is that decode throughput ranks C > A > B, with C gaining 14% over A while B loses 31%.

### Encode

All three approaches produce roughly the same encode time because the output string is short (at most ~87 characters) and the board iteration is identical in structure. Approach C is 20% faster than A because it uses a `StringBuilder` with a pre-sized internal buffer, avoiding the repeated string concatenation and intermediate `String` objects that Approach A produces. Approach B's `foldLeft`-based concatenation surprisingly beats A by 7%, likely because the Scala compiler optimises the lambda chain at the JIT level on this simple case.

### GC Pressure

Approach B creates the most intermediate objects: every `foldLeft` step boxes the accumulator state in a product type, every `Either.flatMap` wraps and unwraps a `Right`, and the `List.:+` append for castling rights produces a new list node per element. Approach C creates the fewest: the `Array.fill(64)` is allocated once per decode call and mutated in-place, and the `StringBuilder` for encode is a single mutable buffer. Approach A sits in between — the mutable `HashMap` and `var` counters avoid functional allocation overhead but still construct a new `HashMap` per decode call.

## Recommendation

**For production:** Approach C (Array.fill + StringBuilder). It is consistently fastest across all three benchmarks (-14% decode, -20% encode, -18% round-trip vs baseline) and creates the least GC pressure, which matters in a game loop executing FEN parsing continuously.

**For maintainability:** Approach A (Imperative baseline). The code reads straightforwardly, the early returns keep error handling local, and the logic mirrors the FEN specification section by section. It sits at a comfortable middle ground between raw performance and readable intent.

**For academic/teaching use:** Approach B (foldLeft + flatMap). It is the most idiomatic functional Scala — no `var`, no mutation, every transformation is a composable pure function — making it the best choice for illustrating how `Either` sequencing and `foldLeft` accumulation work in a real parser.
