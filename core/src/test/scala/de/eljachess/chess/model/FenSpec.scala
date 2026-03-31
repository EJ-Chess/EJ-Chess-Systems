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
