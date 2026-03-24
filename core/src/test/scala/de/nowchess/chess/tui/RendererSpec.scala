// core/src/test/scala/de/nowchess/chess/tui/RendererSpec.scala
package de.nowchess.chess.tui

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import de.nowchess.chess.model.Board

class RendererSpec extends AnyFlatSpec with Matchers:

  val lines: Array[String] = Renderer.render(Board.initial).split("\n")

  "Renderer.render" should "produce 9 lines (8 rows + label line)" in {
    lines.length shouldBe 9
  }

  it should "render row 8 with black back rank" in {
    lines(0) shouldBe "8 ♜ ♞ ♝ ♛ ♚ ♝ ♞ ♜"
  }

  it should "render row 7 with black pawns" in {
    lines(1) shouldBe "7 ♟ ♟ ♟ ♟ ♟ ♟ ♟ ♟"
  }

  it should "render row 6 as empty" in {
    lines(2) shouldBe "6 · · · · · · · ·"
  }

  it should "render row 1 with white back rank" in {
    lines(7) shouldBe "1 ♖ ♘ ♗ ♕ ♔ ♗ ♘ ♖"
  }

  it should "render column labels as the last line" in {
    lines.last shouldBe "  a b c d e f g h"
  }
