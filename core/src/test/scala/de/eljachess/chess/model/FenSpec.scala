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
