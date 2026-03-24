// core/src/test/scala/de/nowchess/chess/model/BoardSpec.scala
package de.nowchess.chess.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BoardSpec extends AnyFlatSpec with Matchers:

  "Board.initial" should "have 32 pieces" in {
    Board.initial.grid.size shouldBe 32
  }

  it should "have a white king at e1" in {
    Board.initial.pieceAt(Square(4, 0)) shouldBe Some(Piece(Color.White, PieceKind.King))
  }

  it should "have a black queen at d8" in {
    Board.initial.pieceAt(Square(3, 7)) shouldBe Some(Piece(Color.Black, PieceKind.Queen))
  }

  it should "have an empty square at e4" in {
    Board.initial.pieceAt(Square(4, 3)) shouldBe None
  }

  "Board.move" should "return Some with updated grid when piece exists at from" in {
    val result = Board.initial.move(Square(4, 1), Square(4, 3))
    result shouldBe defined
    result.get.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
    result.get.pieceAt(Square(4, 1)) shouldBe None
  }

  it should "return None when from square is empty" in {
    Board.initial.move(Square(4, 3), Square(4, 4)) shouldBe None
  }

  it should "replace any piece already at the to square" in {
    val result = Board.initial.move(Square(4, 1), Square(4, 6))
    result.get.pieceAt(Square(4, 6)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
  }
