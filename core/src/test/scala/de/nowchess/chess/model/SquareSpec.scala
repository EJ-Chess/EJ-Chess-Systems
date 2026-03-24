// core/src/test/scala/de/nowchess/chess/model/SquareSpec.scala
package de.nowchess.chess.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SquareSpec extends AnyFlatSpec with Matchers:

  "Square" should "store col and row" in {
    val sq = Square(4, 1)
    sq.col shouldBe 4
    sq.row shouldBe 1
  }

  it should "convert to algebraic notation" in {
    Square(0, 0).toAlgebraic shouldBe "a1"
    Square(4, 1).toAlgebraic shouldBe "e2"
    Square(7, 7).toAlgebraic shouldBe "h8"
  }

  it should "support equality" in {
    Square(3, 4) shouldBe Square(3, 4)
    Square(3, 4) should not be Square(3, 5)
  }
