// core/src/test/scala/de/eljachess/chess/model/JsonSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonSpec extends AnyFlatSpec with Matchers:

  "Json.encode" should "produce valid JSON with fen field" in {
    val ctrl = GameController(Board.initial)
    val json = Json.encode(ctrl, "White", "Black")
    json should include("\"fen\"")
    json should include("\"whiteName\"")
    json should include("\"blackName\"")
    json should include("\"date\"")
  }

  it should "include initial FEN in output" in {
    val ctrl = GameController(Board.initial)
    val json = Json.encode(ctrl)
    json should include("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
  }

  it should "use default player names if not provided" in {
    val ctrl = GameController(Board.initial)
    val json = Json.encode(ctrl)
    json should include("\"whiteName\"")
    json should include("White")
    json should include("\"blackName\"")
    json should include("Black")
  }

  "Json.decode" should "parse valid JSON and return GameController" in {
    val json = """{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","whiteName":"A","blackName":"B","date":"2026-04-07"}"""
    Json.decode(json) match
      case Right(ctrl) => ctrl.board.pieceAt(Square(0, 7)) shouldBe Some(Piece(Color.Black, PieceKind.Rook))
      case Left(err)   => fail(err)
  }

  it should "return Left when fen field is missing" in {
    val json = """{"whiteName":"A","blackName":"B","date":"2026-04-07"}"""
    Json.decode(json) match
      case Left(err) => err should include("fen")
      case Right(_)  => fail("Should have failed")
  }

  it should "return Left when fen is invalid" in {
    val json = """{"fen":"invalid fen","whiteName":"A","blackName":"B","date":"2026-04-07"}"""
    Json.decode(json) match
      case Left(err) => succeed
      case Right(_)  => fail("Should have failed")
  }

  it should "ignore metadata fields on decode" in {
    val json = """{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","whiteName":"Ignored","blackName":"Ignored","date":"2000-01-01"}"""
    Json.decode(json) match
      case Right(ctrl) => ctrl shouldBe GameController(Board.initial)
      case Left(err)   => fail(err)
  }

  it should "handle whitespace and newlines in JSON" in {
    val json = """{
      "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      "whiteName": "A",
      "blackName": "B",
      "date": "2026-04-07"
    }"""
    Json.decode(json) match
      case Right(ctrl) => ctrl.board.pieceAt(Square(0, 7)) shouldBe Some(Piece(Color.Black, PieceKind.Rook))
      case Left(err)   => fail(err)
  }
