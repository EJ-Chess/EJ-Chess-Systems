package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController

object FenBenchmark:

  private val testFens = Vector(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 10"
  )

  private def bench(name: String, warmup: Int, iters: Int)(block: => Unit): Unit =
    // warmup
    var i = 0
    while i < warmup do { block; i += 1 }
    // measure
    val t0 = System.nanoTime()
    i = 0
    while i < iters do { block; i += 1 }
    val elapsed = System.nanoTime() - t0
    val nsPerOp = elapsed.toDouble / iters
    println(f"$name%-30s  $nsPerOp%8.1f ns/op  (${iters} iters)")

  def run(): Unit =
    println("=== FEN Benchmark ===")
    println()

    bench("decode batch (5 FENs)", warmup = 5_000, iters = 50_000) {
      testFens.foreach(Fen.decode)
    }

    bench("encode (initial board)", warmup = 5_000, iters = 50_000) {
      Fen.encode(GameController(Board.initial))
    }

    bench("round-trip (encode+decode)", warmup = 5_000, iters = 50_000) {
      val ctrl = GameController(Board.initial)
      Fen.decode(Fen.encode(ctrl))
    }

    println()
    println("=== ParserCombinatorsFEN (Approach F) ===")
    println()

    bench("decode batch (5 FENs) - ParserCombinatorsFEN", warmup = 5_000, iters = 50_000) {
      testFens.foreach(ParserCombinatorsFEN.parsePlacement)
    }

    println()
    println("Done.")

  @main def fenBenchmark(): Unit = run()

  // Explicit main method for JavaExec compatibility
  def main(args: Array[String]): Unit = run()
