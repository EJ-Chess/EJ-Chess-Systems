// core/src/test/scala/de/eljachess/chess/model/PgnSpec.scala
package de.eljachess.chess.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PgnSpec extends AnyFlatSpec with Matchers:

  "Pgn.decode" should "parse 7-tag header into Map" in {
    val pgnText = "[Event \"Test\"]\n[Site \"?\"]\n[Date \"2026.03.31\"]\n[Round \"?\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n[Result \"*\"]\n\ne4 e5"
    Pgn.decode(pgnText) match
      case Right((headers, moves)) =>
        headers.get("White") shouldBe Some("Alice")
        headers.get("Black") shouldBe Some("Bob")
        headers.get("Event") shouldBe Some("Test")
        moves shouldBe List("e4", "e5")
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "extract only present tags, ignore missing ones" in {
    val pgnText = "[White \"Alice\"]\n[Black \"Bob\"]\n\ne4 e5"
    Pgn.decode(pgnText) match
      case Right((headers, moves)) =>
        headers.size shouldBe 2
        headers.get("White") shouldBe Some("Alice")
        headers.get("Event") shouldBe None
        moves shouldBe List("e4", "e5")
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "reject duplicate tags" in {
    val pgnText = "[White \"Alice\"]\n[White \"Bob\"]\n\ne4"
    Pgn.decode(pgnText) match
      case Left(msg) => msg should include("Duplicate")
      case Right(_)  => fail("Expected Left for duplicate tags")
  }

  it should "return Left on malformed header" in {
    val pgnText = "[White \"Alice\"\ne4"
    Pgn.decode(pgnText) match
      case Left(msg) => msg should not be empty
      case Right(_)  => fail("Expected Left for malformed header")
  }

  it should "parse move list ignoring move numbers and result" in {
    val pgnText = "1. e4 e5 2. Nf3 Nc6 *"
    Pgn.decode(pgnText) match
      case Right((_, moves)) => moves shouldBe List("e4", "e5", "Nf3", "Nc6")
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "ignore comments and annotations in move list" in {
    val pgnText = "e4 {best move} e5! Nf3? Nc6!!"
    Pgn.decode(pgnText) match
      case Right((_, moves)) => moves shouldBe List("e4", "e5", "Nf3", "Nc6")
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "accept empty PGN" in {
    Pgn.decode("") match
      case Right((headers, moves)) =>
        headers.isEmpty shouldBe true
        moves.isEmpty shouldBe true
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "parse headers and moves with CRLF line endings" in {
    val pgnText = "[White \"Alice\"]\r\n[Black \"Bob\"]\r\n\r\ne4 e5"
    Pgn.decode(pgnText) match
      case Right((headers, moves)) =>
        headers.get("White") shouldBe Some("Alice")
        moves shouldBe List("e4", "e5")
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "handle empty lines between headers" in {
    val pgnText = "[White \"Alice\"]\n\n[Black \"Bob\"]\n\ne4 e5"
    Pgn.decode(pgnText) match
      case Right((headers, moves)) =>
        headers.get("White") shouldBe Some("Alice")
        headers.get("Black") shouldBe Some("Bob")
        moves shouldBe List("e4", "e5")
      case Left(err) => fail(s"Expected Right, got: $err")
  }

  it should "report only the first error even when multiple bracket lines are malformed" in {
    // Both lines start with '[' so both land in headerLines — first parse failure propagates through fold
    val pgnText = "[BadHeader]\n[AlsoBad]\n\ne4 e5"
    Pgn.decode(pgnText) match
      case Left(msg) => msg should include("Invalid PGN header")
      case Right(_)  => fail("Expected Left for malformed headers")
  }

  it should "propagate the first error past subsequent header lines in the fold" in {
    // Three bracket-lines: first malformed, second valid, third valid
    // The (Left(err), _) branch of foldLeft must be exercised for lines 2 and 3
    val pgnText = "[BadHeader]\n[White \"Alice\"]\n[Black \"Bob\"]\n\ne4"
    Pgn.decode(pgnText) match
      case Left(msg) => msg should include("Invalid PGN header")
      case Right(_)  => fail("Expected Left for first malformed header")
  }
