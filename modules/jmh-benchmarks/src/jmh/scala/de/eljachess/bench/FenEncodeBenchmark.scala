package de.eljachess.bench

import de.eljachess.chess.controller.GameController
import de.eljachess.chess.model.{Board, Color, Fen, Square}
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class FenEncodeBenchmark:

  var ctrl: GameController = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    ctrl = GameController(Board.initial)

  /** Baseline: encode initial board position to FEN string. */
  @Benchmark
  def encode(): String = Fen.encode(ctrl)

  /** Decode a FEN string into a GameController. */
  @Benchmark
  def decode(): Either[String, GameController] =
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

  /** Full encode+decode round-trip (allocates both). */
  @Benchmark
  def roundTrip(): Either[String, GameController] =
    Fen.decode(Fen.encode(ctrl))

  /** Compute all legal moves from initial position. */
  @Benchmark
  def legalMovesInitial(): List[(Square, Square)] =
    Board.initial.legalMoves(Color.White)
