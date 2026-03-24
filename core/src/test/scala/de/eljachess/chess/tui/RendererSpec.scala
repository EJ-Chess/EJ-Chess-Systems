// core/src/test/scala/de/eljachess/chess/tui/RendererSpec.scala
package de.eljachess.chess.tui

import de.eljachess.chess.model.{Board, Color}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RendererSpec extends AnyFlatSpec with Matchers:

  private def stripAnsi(s: String): String = s.replaceAll("\u001b\\[[0-9;]*m", "")

  val raw: Array[String]     = Renderer.render(Board.initial, Color.White).split("\n")
  val lines: Array[String]   = raw.map(stripAnsi)

  "Renderer.render" should "produce 10 lines (turn line + 8 rows + label line)" in {
    lines.length shouldBe 10
  }

  it should "show whose turn it is on the first line" in {
    lines(0) shouldBe "White's turn"
  }

  it should "show Black's turn when it is Black's turn" in {
    val blackLines = Renderer.render(Board.initial, Color.Black).split("\n").map(stripAnsi)
    blackLines(0) shouldBe "Black's turn"
  }

  it should "render row 8 with black back rank" in {
    lines(1) shouldBe "8  ♜  ♞  ♝  ♛  ♚  ♝  ♞  ♜ "
  }

  it should "render row 7 with black pawns" in {
    lines(2) shouldBe "7  ♟  ♟  ♟  ♟  ♟  ♟  ♟  ♟ "
  }

  it should "render row 6 as empty" in {
    lines(3) shouldBe s"6 ${"   " * 8}"
  }

  it should "render row 1 with white back rank" in {
    lines(8) shouldBe "1  ♖  ♘  ♗  ♕  ♔  ♗  ♘  ♖ "
  }

  it should "render column labels as the last line" in {
    lines.last shouldBe "    a  b  c  d  e  f  g  h "
  }
