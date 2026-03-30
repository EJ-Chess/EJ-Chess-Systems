// core/src/test/scala/de/eljachess/chess/controller/CommandParserSpec.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommandParserSpec extends AnyFlatSpec with Matchers:

  "CommandParser.parse" should "parse a valid move command" in {
    CommandParser.parse("e2 e4") shouldBe Right(ParsedMove.Move(Square(4, 1), Square(4, 3), None))
  }

  it should "parse corner squares" in {
    CommandParser.parse("a1 h8") shouldBe Right(ParsedMove.Move(Square(0, 0), Square(7, 7), None))
  }

  it should "parse a promotion move with Q" in {
    CommandParser.parse("e7 e8 Q") shouldBe Right(ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen)))
  }

  it should "parse a promotion move with R" in {
    CommandParser.parse("e7 e8 R") shouldBe Right(ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Rook)))
  }

  it should "parse a promotion move with B" in {
    CommandParser.parse("e7 e8 B") shouldBe Right(ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Bishop)))
  }

  it should "parse a promotion move with N" in {
    CommandParser.parse("e7 e8 N") shouldBe Right(ParsedMove.Move(Square(4, 6), Square(4, 7), Some(PieceKind.Knight)))
  }

  it should "return Left for an invalid promotion token" in {
    CommandParser.parse("e7 e8 X") shouldBe a[Left[?, ?]]
  }

  it should "parse O-O as kingside castling" in {
    CommandParser.parse("O-O") shouldBe Right(ParsedMove.Castling(kingside = true))
  }

  it should "parse O-O-O as queenside castling" in {
    CommandParser.parse("O-O-O") shouldBe Right(ParsedMove.Castling(kingside = false))
  }

  it should "return Left for a single token" in {
    CommandParser.parse("e2") shouldBe a[Left[?, ?]]
  }

  it should "return Left for four tokens" in {
    CommandParser.parse("e2 e4 e6 e8") shouldBe a[Left[?, ?]]
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

  it should "return the exact error message for unknown format" in {
    val Left(msg) = CommandParser.parse("foo"): @unchecked
    msg shouldBe "Invalid command format. Use: <from> <to> (e.g. e2 e4)"
  }
