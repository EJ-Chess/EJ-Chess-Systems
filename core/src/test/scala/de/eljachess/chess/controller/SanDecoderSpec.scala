package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SanDecoderSpec extends AnyFlatSpec with Matchers:

  "SanDecoder.expand" should "expand pawn move e4 from initial position" in {
    SanDecoder.expand(Board.initial, Color.White, "e4") match
      case Right((from, to, promo)) =>
        from shouldBe Square(4, 1)  // e2
        to   shouldBe Square(4, 3)  // e4
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand knight move Nf3 from initial position" in {
    SanDecoder.expand(Board.initial, Color.White, "Nf3") match
      case Right((from, to, promo)) =>
        from shouldBe Square(6, 0)  // g1
        to   shouldBe Square(5, 2)  // f3
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand pawn capture exd5" in {
    // pawn on e4 captures d5
    val board = Board.initial
      .move(Square(4, 1), Square(4, 3)).get  // e2-e4 (white)
      .move(Square(3, 6), Square(3, 4)).get  // d7-d5 (black)
    SanDecoder.expand(board, Color.White, "exd5") match
      case Right((from, to, promo)) =>
        from shouldBe Square(4, 3)  // e4
        to   shouldBe Square(3, 4)  // d5
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand kingside castling O-O for White" in {
    SanDecoder.expand(Board.initial, Color.White, "O-O") match
      case Right((from, to, promo)) =>
        from shouldBe Square(4, 0)  // e1
        to   shouldBe Square(6, 0)  // g1
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand queenside castling O-O-O for Black" in {
    SanDecoder.expand(Board.initial, Color.Black, "O-O-O") match
      case Right((from, to, promo)) =>
        from shouldBe Square(4, 7)  // e8
        to   shouldBe Square(2, 7)  // c8
        promo shouldBe None
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "expand promotion e8=Q" in {
    // FEN: white pawn on e7, black king on f8 (e8 empty), white king on e1
    val board = Fen.decode("5k2/4P3/8/8/8/8/8/4K3 w - - 0 1").map(_.board).getOrElse(Board.initial)
    SanDecoder.expand(board, Color.White, "e8=Q") match
      case Right((from, to, promo)) =>
        from  shouldBe Square(4, 6)  // e7
        to    shouldBe Square(4, 7)  // e8
        promo shouldBe Some(PieceKind.Queen)
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "ignore check/mate suffix (+/#)" in {
    val r1 = SanDecoder.expand(Board.initial, Color.White, "Nf3")
    val r2 = SanDecoder.expand(Board.initial, Color.White, "Nf3+")
    val r3 = SanDecoder.expand(Board.initial, Color.White, "Nf3#")
    r1 shouldBe r2
    r1 shouldBe r3
  }

  it should "return Left for invalid destination square" in {
    SanDecoder.expand(Board.initial, Color.White, "Nf9").isLeft shouldBe true
  }

  it should "return Left when no piece can make the move" in {
    // Nf6 is not reachable from initial position for White
    SanDecoder.expand(Board.initial, Color.White, "Nf6") match
      case Left(msg) => msg.toLowerCase should include("illegal")
      case Right(_)  => fail("Expected Left for impossible move")
  }

  it should "disambiguate with file when two knights can reach d1" in {
    // Knights on c3 and e3 both can reach d1 — use Ncd1 to pick c-file knight
    val board = Fen.decode("8/8/8/8/8/2N1N3/8/4K3 w - - 0 1").map(_.board).getOrElse(Board.initial)
    SanDecoder.expand(board, Color.White, "Ncd1") match
      case Right((from, _, _)) => from.toAlgebraic.head shouldBe 'c'
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "return Left for ambiguous move without disambiguation" in {
    val board = Fen.decode("8/8/8/8/8/2N1N3/8/4K3 w - - 0 1").map(_.board).getOrElse(Board.initial)
    SanDecoder.expand(board, Color.White, "Nd1") match
      case Left(msg) => msg.toLowerCase should include("ambiguous")
      case Right(_)  => fail("Expected Left for ambiguous move")
  }
