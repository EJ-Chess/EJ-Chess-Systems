// core/src/main/scala/de/eljachess/chess/model/BoardMonad.scala
package de.eljachess.chess.model

/** Generic Monad typeclass.
  *
  * Defines the three fundamental operations:
  *   - [[pure]]    – lift a pure value into the context
  *   - [[flatMap]] – sequentially compose effectful computations
  *   - [[map]]     – derived from flatMap; transform a value without changing the context
  */
trait Monad[F[_]]:
  def pure[A](a: A): F[A]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def map[A, B](fa: F[A])(f: A => B): F[B] =
    flatMap(fa)(a => pure(f(a)))

/** A Board state monad: a computation that, given a [[Board]], may produce
  * a new [[Board]] together with a result value, or fail (represented as [[None]]).
  *
  * Essentially `Board => Option[(Board, A)]` – a State monad over [[Board]]
  * with built-in failure handling.
  *
  * The [[Monad]] instance allows chaining chess operations with `flatMap`/`map`
  * while threading the board state through automatically.
  */
opaque type BoardM[+A] = Board => Option[(Board, A)]

object BoardM:

  /** Build a [[BoardM]] from an explicit function. */
  def apply[A](f: Board => Option[(Board, A)]): BoardM[A] = f

  /** Execute the computation against a concrete [[Board]]. */
  def run[A](bm: BoardM[A])(board: Board): Option[(Board, A)] = bm(board)

  /** Lift a [[Board]]-transforming function (e.g. a move) into [[BoardM]].
    * The result value is [[Unit]] – the new state is in the board. */
  def liftMove(f: Board => Option[Board]): BoardM[Unit] =
    board => f(board).map(newBoard => (newBoard, ()))

  /** Read the current board state as a value without modifying it. */
  val get: BoardM[Board] = board => Some((board, board))

  /** Replace the current board state. */
  def set(newBoard: Board): BoardM[Unit] = _ => Some((newBoard, ()))

  /** The [[Monad]] instance for [[BoardM]]. */
  given monadInstance: Monad[BoardM] with

    /** Inject a pure value: board is passed through unchanged. */
    def pure[A](a: A): BoardM[A] =
      board => Some((board, a))

    /** Chain two [[BoardM]] computations: the first computes a value and a new
      * board state; the second continues from that new state. */
    def flatMap[A, B](fa: BoardM[A])(f: A => BoardM[B]): BoardM[B] =
      board =>
        fa(board).flatMap { case (newBoard, a) =>
          f(a)(newBoard)
        }

  // ── Extension methods (for-comprehension / fluent syntax) ────────────────

  extension [A](fa: BoardM[A])
    def flatMap[B](f: A => BoardM[B])(using m: Monad[BoardM]): BoardM[B] =
      m.flatMap(fa)(f)
    def map[B](f: A => B)(using m: Monad[BoardM]): BoardM[B] =
      m.map(fa)(f)
    @scala.annotation.targetName("runOn")
    def run(board: Board): Option[(Board, A)] = fa(board)
