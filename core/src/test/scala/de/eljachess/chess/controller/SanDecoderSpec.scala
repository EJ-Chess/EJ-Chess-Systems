// core/src/test/scala/de/eljachess/chess/controller/SanDecoderSpec.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SanDecoderSpec extends AnyFlatSpec with Matchers:

  "SanDecoder.expand" should "expand pawn move e4 from initial position" in {
    SanDecoder.expand(Board.initial)(Color.White)("e4") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 1)  // e2
        to    shouldBe Square(4, 3)  // e4
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand knight move Nf3 from initial position" in {
    SanDecoder.expand(Board.initial)(Color.White)("Nf3") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(6, 0)  // g1
        to    shouldBe Square(5, 2)  // f3
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand pawn capture exd5" in {
    val board = Board.initial
      .move(Square(4, 1), Square(4, 3)).get  // e2-e4 (white)
      .move(Square(3, 6), Square(3, 4)).get  // d7-d5 (black)
    SanDecoder.expand(board)(Color.White)("exd5") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 3)  // e4
        to    shouldBe Square(3, 4)  // d5
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand kingside castling O-O for White" in {
    SanDecoder.expand(Board.initial)(Color.White)("O-O") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 0)  // e1
        to    shouldBe Square(6, 0)  // g1
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand queenside castling O-O-O for Black" in {
    SanDecoder.expand(Board.initial)(Color.Black)("O-O-O") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 7)  // e8
        to    shouldBe Square(2, 7)  // c8
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand promotion e8=Q" in {
    val board = Fen.decode("5k2/4P3/8/8/8/8/8/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("e8=Q") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 6)  // e7
        to    shouldBe Square(4, 7)  // e8
        promo shouldBe Some(PieceKind.Queen)
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "ignore check/mate suffix (+/#)" in {
    val r1 = SanDecoder.expand(Board.initial)(Color.White)("Nf3")
    val r2 = SanDecoder.expand(Board.initial)(Color.White)("Nf3+")
    val r3 = SanDecoder.expand(Board.initial)(Color.White)("Nf3#")
    r1 shouldBe r2
    r1 shouldBe r3
  }

  it should "return Left for invalid destination square" in {
    SanDecoder.expand(Board.initial)(Color.White)("Nf9").isLeft shouldBe true
  }

  it should "return Left when no piece can make the move" in {
    SanDecoder.expand(Board.initial)(Color.White)("Nf6") match
      case Left(msg) => msg should include("No piece can make move")
      case Right(_)  => fail("Expected Left for impossible move")
  }

  it should "disambiguate with file when two knights can reach d1" in {
    val board = Fen.decode("8/8/8/8/8/2N1N3/8/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("Ncd1") match
      case Right((from, _, _)) => from.toAlgebraic.head shouldBe 'c'
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "return Left for ambiguous move without disambiguation" in {
    val board = Fen.decode("8/8/8/8/8/2N1N3/8/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("Nd1") match
      case Left(msg) => msg.toLowerCase should include("ambiguous")
      case Right(_)  => fail("Expected Left for ambiguous move")
  }

  it should "expand kingside castling O-O for Black" in {
    SanDecoder.expand(Board.initial)(Color.Black)("O-O") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 7)  // e8
        to    shouldBe Square(6, 7)  // g8
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand promotion e8=R to Rook" in {
    val board = Fen.decode("5k2/4P3/8/8/8/8/8/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("e8=R") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 6)
        to    shouldBe Square(4, 7)
        promo shouldBe Some(PieceKind.Rook)
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "disambiguate with rank when two knights on same file" in {
    val board = Fen.decode("8/8/8/8/8/1n6/8/1n2k3 b - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.Black)("N3d2") match
      case Right((from, _, _)) => from.toAlgebraic.last shouldBe '3'
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "return Left for pawn capture when target is empty" in {
    val board = Board.initial
      .move(Square(4, 1), Square(4, 3)).get  // e2-e4 (white)
    SanDecoder.expand(board)(Color.White)("exd5") match
      case Left(msg) => msg should not be empty
      case Right(_)  => fail("Expected Left when capture target is empty")
  }

  it should "expand queenside castling O-O-O for White" in {
    SanDecoder.expand(Board.initial)(Color.White)("O-O-O") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 0)  // e1
        to    shouldBe Square(2, 0)  // c1
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand promotion e8=B to Bishop" in {
    val board = Fen.decode("5k2/4P3/8/8/8/8/8/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("e8=B") match
      case Right((_, _, promo)) => promo shouldBe Some(PieceKind.Bishop)
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand promotion e8=N to Knight" in {
    val board = Fen.decode("5k2/4P3/8/8/8/8/8/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("e8=N") match
      case Right((_, _, promo)) => promo shouldBe Some(PieceKind.Knight)
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand bishop move Ba4 from open position" in {
    val board = Fen.decode("4k3/8/8/8/8/8/2B5/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("Ba4") match
      case Right((from, to, _)) =>
        from shouldBe Square(2, 1)  // c2
        to   shouldBe Square(0, 3)  // a4
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand rook move Ra5 from open position" in {
    val board = Fen.decode("4k3/8/8/8/8/8/8/R3K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("Ra5") match
      case Right((from, to, _)) =>
        from shouldBe Square(0, 0)  // a1
        to   shouldBe Square(0, 4)  // a5
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand queen move Qd3 from open position" in {
    val board = Fen.decode("4k3/8/8/8/8/8/8/3QK3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("Qd3") match
      case Right((from, to, _)) =>
        from shouldBe Square(3, 0)  // d1
        to   shouldBe Square(3, 2)  // d3
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand king move Ke2 from open position" in {
    val board = Fen.decode("4k3/8/8/8/8/8/8/4K3 w - - 0 1") match
      case Right(ctrl) => ctrl.board
      case Left(err)   => fail(s"FEN decode failed: $err")
    SanDecoder.expand(board)(Color.White)("Ke2") match
      case Right((from, to, _)) =>
        from shouldBe Square(4, 0)  // e1
        to   shouldBe Square(4, 1)  // e2
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "return Left for completely invalid SAN syntax" in {
    SanDecoder.expand(Board.initial)(Color.White)("!!!") match
      case Left(msg) => msg should include("Invalid SAN syntax")
      case Right(_)  => fail("Expected Left for invalid SAN")
  }

  // ── Currying-specific tests ──────────────────────────────────────────────────

  it should "support partial application: fix board and color, decode multiple SANs" in {
    val decode = SanDecoder.expand(Board.initial)(Color.White)
    val e4     = decode("e4")
    val nf3    = decode("Nf3")
    e4.isRight  shouldBe true
    nf3.isRight shouldBe true
  }

  it should "support partial application: fix only the board" in {
    val forBoard = SanDecoder.expand(Board.initial)
    forBoard(Color.White)("e4").isRight shouldBe true
    forBoard(Color.Black)("e5").isRight shouldBe true
  }

  it should "allow passing the curried decoder as a function value" in {
    val expand: Color => String => Either[String, (Square, Square, Option[PieceKind])] =
      SanDecoder.expand(Board.initial)

    List(
      expand(Color.White)("e4"),
      expand(Color.White)("Nf3"),
      expand(Color.White)("d4")
    ).foreach(_.isRight shouldBe true)
  }
