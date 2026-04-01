// core/src/test/scala/de/eljachess/chess/model/BoardMonadSpec.scala
package de.eljachess.chess.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import BoardM.given

class BoardMonadSpec extends AnyFlatSpec with Matchers:

  private val m = summon[Monad[BoardM]]

  // ── pure ────────────────────────────────────────────────────────────────────

  "Monad[BoardM].pure" should "return the value unchanged and leave the board intact" in {
    val result = m.pure(42).run(Board.initial)
    result shouldBe Some((Board.initial, 42))
  }

  it should "work with non-Int types" in {
    val result = m.pure("hello").run(Board.initial)
    result shouldBe Some((Board.initial, "hello"))
  }

  // ── flatMap ──────────────────────────────────────────────────────────────────

  "Monad[BoardM].flatMap" should "thread the board state through two computations" in {
    val move1 = BoardM.liftMove(_.move(Square(4, 1), Square(4, 3)))  // e2-e4
    val move2 = BoardM.liftMove(_.move(Square(4, 6), Square(4, 4)))  // e7-e5

    val program = m.flatMap(move1)(_ => move2)
    val result  = program.run(Board.initial)

    result shouldBe defined
    val (finalBoard, _) = result.get
    finalBoard.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
    finalBoard.pieceAt(Square(4, 4)) shouldBe Some(Piece(Color.Black, PieceKind.Pawn))
  }

  it should "propagate failure when an intermediate move is illegal" in {
    val illegalMove = BoardM.liftMove(_.move(Square(0, 0), Square(7, 7))) // rook can't jump
    val move2       = BoardM.liftMove(_.move(Square(4, 1), Square(4, 3)))

    val program = m.flatMap(illegalMove)(_ => move2)
    program.run(Board.initial) shouldBe None
  }

  // ── map ──────────────────────────────────────────────────────────────────────

  "Monad[BoardM].map" should "transform the result value without changing the board" in {
    val countPieces: BoardM[Int] = BoardM(board => Some((board, board.grid.size)))
    val result = m.map(countPieces)(_ * 2).run(Board.initial)
    result shouldBe Some((Board.initial, 64))
  }

  // ── get / set ────────────────────────────────────────────────────────────────

  "BoardM.get" should "expose the current board as the result value" in {
    val result = BoardM.get.run(Board.initial)
    result shouldBe Some((Board.initial, Board.initial))
  }

  "BoardM.set" should "replace the board state" in {
    val emptyBoard = Board(Map.empty)
    val result     = BoardM.set(emptyBoard).run(Board.initial)
    result shouldBe Some((emptyBoard, ()))
  }

  // ── Monad laws ───────────────────────────────────────────────────────────────

  "Monad[BoardM]" should "satisfy left identity: pure(a).flatMap(f) == f(a)" in {
    val f: Int => BoardM[Int] = n => BoardM(board => Some((board, n * 2)))
    val lhs = m.flatMap(m.pure(5))(f).run(Board.initial)
    val rhs = f(5).run(Board.initial)
    lhs shouldBe rhs
  }

  it should "satisfy right identity: fa.flatMap(pure) == fa" in {
    val fa: BoardM[Int] = BoardM(board => Some((board, board.grid.size)))
    val lhs = m.flatMap(fa)(m.pure).run(Board.initial)
    val rhs = fa.run(Board.initial)
    lhs shouldBe rhs
  }

  it should "satisfy associativity: (fa.flatMap(f)).flatMap(g) == fa.flatMap(a => f(a).flatMap(g))" in {
    val fa: BoardM[Int] = BoardM(board => Some((board, 1)))
    val f: Int => BoardM[Int] = n => BoardM(board => Some((board, n + 10)))
    val g: Int => BoardM[Int] = n => BoardM(board => Some((board, n * 2)))

    val lhs = m.flatMap(m.flatMap(fa)(f))(g).run(Board.initial)
    val rhs = m.flatMap(fa)(a => m.flatMap(f(a))(g)).run(Board.initial)
    lhs shouldBe rhs
  }

  // ── extension methods / for-comprehension ────────────────────────────────────

  "BoardM extension methods" should "allow for-comprehension syntax for chaining moves" in {
    import BoardM.given

    val program: BoardM[Int] =
      for
        _     <- BoardM.liftMove(_.move(Square(4, 1), Square(4, 3)))  // e2-e4
        _     <- BoardM.liftMove(_.move(Square(4, 6), Square(4, 4)))  // e7-e5
        board <- BoardM.get
      yield board.grid.size

    val result = program.run(Board.initial)
    result shouldBe defined
    result.get._2 shouldBe 32  // 32 pieces still on the board
  }

  it should "allow partial application of liftMove" in {
    val applyMove: Square => Square => BoardM[Unit] =
      from => to => BoardM.liftMove(_.move(from, to))

    val moveE2E4 = applyMove(Square(4, 1))(Square(4, 3))
    val result   = moveE2E4.run(Board.initial)

    result shouldBe defined
    result.get._1.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
  }
