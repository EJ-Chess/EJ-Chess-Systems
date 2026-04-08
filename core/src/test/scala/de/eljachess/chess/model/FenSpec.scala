// core/src/test/scala/de/eljachess/chess/model/FenSpec.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FenSpec extends AnyFlatSpec with Matchers:

  // ── Encode — piece placement ──────────────────────────────────────────────

  "Fen.encode" should "produce the correct piece placement for the initial board" in {
    Fen.encode(GameController(Board.initial)).split(" ")(0) shouldBe
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
  }

  it should "encode a single White King on e1 correctly" in {
    val b = Board(Map(Square(4, 0) -> Piece(Color.White, PieceKind.King)))
    Fen.encode(GameController(b)).split(" ")(0) shouldBe "8/8/8/8/8/8/8/4K3"
  }

  it should "encode Black pieces as lowercase" in {
    val b = Board(Map(Square(0, 7) -> Piece(Color.Black, PieceKind.Rook)))
    Fen.encode(GameController(b)).split(" ")(0) shouldBe "r7/8/8/8/8/8/8/8"
  }

  // ── Encode — state fields ─────────────────────────────────────────────────

  it should "encode White to move as 'w'" in {
    Fen.encode(GameController(Board.initial)).split(" ")(1) shouldBe "w"
  }

  it should "encode Black to move as 'b'" in {
    val ctrl = GameController(Board.initial, currentTurn = Color.Black)
    Fen.encode(ctrl).split(" ")(1) shouldBe "b"
  }

  it should "encode all castling rights as 'KQkq'" in {
    Fen.encode(GameController(Board.initial)).split(" ")(2) shouldBe "KQkq"
  }

  it should "encode partial castling rights correctly" in {
    val b = Board(Board.initial.grid,
      CastlingRights(whiteKingside = true, whiteQueenside = false,
                     blackKingside = false, blackQueenside = true))
    Fen.encode(GameController(b)).split(" ")(2) shouldBe "Kq"
  }

  it should "encode no castling rights as '-'" in {
    val b = Board(Board.initial.grid, CastlingRights(false, false, false, false))
    Fen.encode(GameController(b)).split(" ")(2) shouldBe "-"
  }

  it should "encode an en passant target as its algebraic square" in {
    val b = Board(Board.initial.grid, enPassantTarget = Some(Square(4, 2)))
    Fen.encode(GameController(b)).split(" ")(3) shouldBe "e3"
  }

  it should "encode absent en passant as '-'" in {
    Fen.encode(GameController(Board.initial)).split(" ")(3) shouldBe "-"
  }

  it should "encode halfmoveClock correctly" in {
    val ctrl = GameController(Board.initial, halfmoveClock = 7)
    Fen.encode(ctrl).split(" ")(4) shouldBe "7"
  }

  it should "encode fullmoveNumber correctly" in {
    val ctrl = GameController(Board.initial, fullmoveNumber = 3)
    Fen.encode(ctrl).split(" ")(5) shouldBe "3"
  }

  // ── Decode — round-trip ───────────────────────────────────────────────────

  "Fen.decode" should "round-trip the initial position" in {
    Fen.decode(Fen.encode(GameController(Board.initial))) shouldBe
      Right(GameController(Board.initial))
  }

  it should "round-trip a mid-game position preserving all fields" in {
    val ctrl = GameController(
      Board(Board.initial.grid,
            CastlingRights(whiteKingside = true, whiteQueenside = false,
                           blackKingside = true, blackQueenside = false),
            Some(Square(4, 2))),
      currentTurn    = Color.Black,
      halfmoveClock  = 5,
      fullmoveNumber = 3
    )
    Fen.decode(Fen.encode(ctrl)) shouldBe Right(ctrl)
  }

  // ── Decode — known string ─────────────────────────────────────────────────

  it should "decode the initial FEN string to the initial GameController" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
      Right(GameController(Board.initial))
  }

  // ── Decode — error cases ──────────────────────────────────────────────────

  it should "return Left for wrong field count (5 fields)" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0") shouldBe
      Left("Invalid FEN: expected 6 fields, got 5")
  }

  it should "return Left for wrong field count (7 fields)" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 extra") shouldBe
      Left("Invalid FEN: expected 6 fields, got 7")
  }

  it should "return Left for an invalid piece character" in {
    Fen.decode("Xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
      Left("Invalid FEN: invalid piece char 'X'")
  }

  it should "return Left when a rank sum is not 8" in {
    Fen.decode("rnbqkbn/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
      Left("Invalid FEN: rank 1 has wrong length")
  }

  it should "return Left for wrong rank count" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1") shouldBe
      Left("Invalid FEN: expected 8 ranks, got 7")
  }

  it should "return Left for invalid active color" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1") shouldBe
      Left("Invalid FEN: invalid active color 'x'")
  }

  it should "return Left for invalid castling string" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w ZQkq - 0 1") shouldBe
      Left("Invalid FEN: invalid castling 'ZQkq'")
  }

  it should "return Left for invalid en passant square" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e9 0 1") shouldBe
      Left("Invalid FEN: invalid en passant square 'e9'")
  }

  it should "return Left for non-integer halfmove clock" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - x 1") shouldBe
      Left("Invalid FEN: invalid halfmove clock 'x'")
  }

  it should "return Left for zero fullmove number" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0") shouldBe
      Left("Invalid FEN: invalid fullmove number '0'")
  }

  it should "accept halfmove clock of 0" in {
    Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
      Right(GameController(Board.initial))
  }

  it should "decode '-' castling field as no castling rights" in {
    val Right(ctrl) = Fen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"): @unchecked
    ctrl.board.castlingRights shouldBe CastlingRights(false, false, false, false)
  }

  // ── ParserCombinatorsFEN.parsePlacement ───────────────────────────────────

  "ParserCombinatorsFEN.parsePlacement" should "parse initial position" in {
    val placement = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    val Right(expected) = Fen.decode(s"$placement w KQkq - 0 1"): @unchecked
    val result = ParserCombinatorsFEN.parsePlacement(placement)
    result shouldBe Right(expected.board.grid)
  }

  it should "parse empty board (all 8s)" in {
    val result = ParserCombinatorsFEN.parsePlacement("8/8/8/8/8/8/8/8")
    result shouldBe Right(Map())
  }

  it should "parse mixed rank with pieces and empty squares" in {
    val result = ParserCombinatorsFEN.parsePlacement("r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R")
    result.isRight should be(true)
    result.getOrElse(Map()).size should be >= 24
  }

  it should "reject placement with wrong rank count" in {
    val result = ParserCombinatorsFEN.parsePlacement("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP")
    result.isLeft should be(true)
  }

  it should "reject rank with invalid characters by throwing RuntimeException" in {
    // cats.parse propagates sys.error from map when partial parse succeeds but col != 8
    a [RuntimeException] should be thrownBy {
      ParserCombinatorsFEN.parsePlacement("rnbqkbnr/pppppppp/8/8/8/8/PPPPXPPP/RNBQKBNR")
    }
  }

  it should "reject rank containing digit 9 (out of range)" in {
    // '9' is not matched by digitParser (only '1'..'8'), so the parser fails → Left
    val result = ParserCombinatorsFEN.parsePlacement("rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR")
    result.isLeft should be(true)
  }

  it should "parse rank 4 mid-game position" in {
    val result = ParserCombinatorsFEN.parsePlacement("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8")
    result.isRight should be(true)
  }

  it should "parse position with all piece types and return 32 pieces" in {
    val result = ParserCombinatorsFEN.parsePlacement("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
    result.isRight should be(true)
    result.getOrElse(Map()).size shouldBe 32
  }
