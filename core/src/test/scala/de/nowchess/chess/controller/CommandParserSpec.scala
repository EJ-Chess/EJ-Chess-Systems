// core/src/test/scala/de/nowchess/chess/controller/CommandParserSpec.scala
package de.nowchess.chess.controller

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import de.nowchess.chess.model.Square

class CommandParserSpec extends AnyFlatSpec with Matchers:

  "CommandParser.parse" should "parse a valid command" in {
    CommandParser.parse("e2 e4") shouldBe Right((Square(4, 1), Square(4, 3)))
  }

  it should "parse corner squares" in {
    CommandParser.parse("a1 h8") shouldBe Right((Square(0, 0), Square(7, 7)))
  }

  it should "return Left for a single token" in {
    CommandParser.parse("e2") shouldBe a[Left[?, ?]]
  }

  it should "return Left for three tokens" in {
    CommandParser.parse("e2 e4 e6") shouldBe a[Left[?, ?]]
  }

  it should "return Left for invalid column letter" in {
    CommandParser.parse("z2 e4") shouldBe a[Left[?, ?]]
  }

  it should "return Left for invalid row digit" in {
    CommandParser.parse("e0 e4") shouldBe a[Left[?, ?]]
  }

  it should "return Left for empty input" in {
    CommandParser.parse("") shouldBe a[Left[?, ?]]
  }

  it should "return the exact error message" in {
    val Left(msg) = CommandParser.parse("foo"): @unchecked
    msg shouldBe "Invalid command format. Use: <from> <to> (e.g. e2 e4)"
  }
