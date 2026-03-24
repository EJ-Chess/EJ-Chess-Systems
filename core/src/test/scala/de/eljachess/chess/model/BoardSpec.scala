// core/src/test/scala/de/eljachess/chess/model/BoardSpec.scala
package de.eljachess.chess.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BoardSpec extends AnyFlatSpec with Matchers:

  "Board.initial" should "have 32 pieces" in {
    Board.initial.grid.size shouldBe 32
  }

  it should "have a white king at e1" in {
    Board.initial.pieceAt(Square(4, 0)) shouldBe Some(Piece(Color.White, PieceKind.King))
  }

  it should "have a black queen at d8" in {
    Board.initial.pieceAt(Square(3, 7)) shouldBe Some(Piece(Color.Black, PieceKind.Queen))
  }

  it should "have an empty square at e4" in {
    Board.initial.pieceAt(Square(4, 3)) shouldBe None
  }

  "Board.move" should "return None when from square is empty" in {
    Board.initial.move(Square(4, 3), Square(4, 4)) shouldBe None
  }

  // ── White pawn ──────────────────────────────────────────────────────────────

  "Board.move for a white pawn" should "allow one step forward" in {
    val result = Board.initial.move(Square(4, 1), Square(4, 2))   // e2-e3
    result shouldBe defined
    result.get.pieceAt(Square(4, 2)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
    result.get.pieceAt(Square(4, 1)) shouldBe None
  }

  it should "allow two steps forward from starting rank" in {
    val result = Board.initial.move(Square(4, 1), Square(4, 3))   // e2-e4
    result shouldBe defined
    result.get.pieceAt(Square(4, 3)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
  }

  it should "not allow two steps forward when not on starting rank" in {
    val afterOne = Board.initial.move(Square(4, 1), Square(4, 2)).get
    afterOne.move(Square(4, 2), Square(4, 4)) shouldBe None
  }

  it should "not allow moving forward when the target is blocked" in {
    val blocked = Board(Board.initial.grid + (Square(4, 2) -> Piece(Color.Black, PieceKind.Pawn)))
    blocked.move(Square(4, 1), Square(4, 2)) shouldBe None
  }

  it should "not allow two steps when the intermediate square is blocked" in {
    val blocked = Board(Board.initial.grid + (Square(4, 2) -> Piece(Color.Black, PieceKind.Pawn)))
    blocked.move(Square(4, 1), Square(4, 3)) shouldBe None
  }

  it should "not allow two steps when the target square is blocked" in {
    val blocked = Board(Board.initial.grid + (Square(4, 3) -> Piece(Color.Black, PieceKind.Pawn)))
    blocked.move(Square(4, 1), Square(4, 3)) shouldBe None
  }

  it should "allow a diagonal capture" in {
    val g = Map(
      Square(3, 4) -> Piece(Color.White, PieceKind.Pawn),
      Square(4, 5) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val result = Board(g).move(Square(3, 4), Square(4, 5))
    result shouldBe defined
    result.get.pieceAt(Square(4, 5)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
  }

  it should "not allow a diagonal move to an empty square" in {
    Board.initial.move(Square(4, 1), Square(5, 2)) shouldBe None
  }

  it should "not allow capturing a friendly piece diagonally" in {
    val g = Map(
      Square(3, 4) -> Piece(Color.White, PieceKind.Pawn),
      Square(4, 5) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(3, 4), Square(4, 5)) shouldBe None
  }

  it should "not allow moving backwards" in {
    val g = Map(Square(4, 3) -> Piece(Color.White, PieceKind.Pawn))
    Board(g).move(Square(4, 3), Square(4, 2)) shouldBe None
  }

  // ── Black pawn ──────────────────────────────────────────────────────────────

  "Board.move for a black pawn" should "allow one step forward (down)" in {
    val result = Board.initial.move(Square(4, 6), Square(4, 5))   // e7-e6
    result shouldBe defined
    result.get.pieceAt(Square(4, 5)) shouldBe Some(Piece(Color.Black, PieceKind.Pawn))
  }

  it should "allow two steps forward from starting rank" in {
    Board.initial.move(Square(4, 6), Square(4, 4)) shouldBe defined  // e7-e5
  }

  it should "not allow two steps when not on starting rank" in {
    val afterOne = Board.initial.move(Square(4, 6), Square(4, 5)).get
    afterOne.move(Square(4, 5), Square(4, 3)) shouldBe None
  }

  it should "allow a diagonal capture" in {
    val g = Map(
      Square(4, 4) -> Piece(Color.Black, PieceKind.Pawn),
      Square(3, 3) -> Piece(Color.White, PieceKind.Pawn)
    )
    val result = Board(g).move(Square(4, 4), Square(3, 3))
    result shouldBe defined
    result.get.pieceAt(Square(3, 3)) shouldBe Some(Piece(Color.Black, PieceKind.Pawn))
  }

  it should "not allow a diagonal move to an empty square" in {
    Board.initial.move(Square(4, 6), Square(5, 5)) shouldBe None
  }

  // ── Rook ────────────────────────────────────────────────────────────────────

  "Board.move for a rook" should "allow moving vertically" in {
    val g = Map(Square(0, 0) -> Piece(Color.White, PieceKind.Rook))
    Board(g).move(Square(0, 0), Square(0, 5)) shouldBe defined
  }

  it should "allow moving horizontally" in {
    val g = Map(Square(0, 0) -> Piece(Color.White, PieceKind.Rook))
    Board(g).move(Square(0, 0), Square(5, 0)) shouldBe defined
  }

  it should "not allow moving diagonally" in {
    val g = Map(Square(0, 0) -> Piece(Color.White, PieceKind.Rook))
    Board(g).move(Square(0, 0), Square(3, 3)) shouldBe None
  }

  it should "not jump over a friendly piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 2) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(0, 0), Square(0, 5)) shouldBe None
  }

  it should "not jump over an enemy piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 2) -> Piece(Color.Black, PieceKind.Pawn)
    )
    Board(g).move(Square(0, 0), Square(0, 5)) shouldBe None
  }

  it should "capture an enemy piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 4) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val result = Board(g).move(Square(0, 0), Square(0, 4))
    result shouldBe defined
    result.get.pieceAt(Square(0, 4)) shouldBe Some(Piece(Color.White, PieceKind.Rook))
    result.get.grid.size shouldBe 1
  }

  it should "not capture a friendly piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 4) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(0, 0), Square(0, 4)) shouldBe None
  }

  // ── Bishop (Läufer) ─────────────────────────────────────────────────────────

  "Board.move for a bishop" should "allow moving diagonally" in {
    val g = Map(Square(0, 0) -> Piece(Color.White, PieceKind.Bishop))
    Board(g).move(Square(0, 0), Square(4, 4)) shouldBe defined
  }

  it should "not allow moving horizontally" in {
    val g = Map(Square(0, 0) -> Piece(Color.White, PieceKind.Bishop))
    Board(g).move(Square(0, 0), Square(4, 0)) shouldBe None
  }

  it should "not allow moving vertically" in {
    val g = Map(Square(0, 0) -> Piece(Color.White, PieceKind.Bishop))
    Board(g).move(Square(0, 0), Square(0, 4)) shouldBe None
  }

  it should "not jump over a piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Bishop),
      Square(2, 2) -> Piece(Color.Black, PieceKind.Pawn)
    )
    Board(g).move(Square(0, 0), Square(4, 4)) shouldBe None
  }

  it should "capture an enemy piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Bishop),
      Square(3, 3) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val result = Board(g).move(Square(0, 0), Square(3, 3))
    result shouldBe defined
    result.get.pieceAt(Square(3, 3)) shouldBe Some(Piece(Color.White, PieceKind.Bishop))
  }

  it should "not capture a friendly piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Bishop),
      Square(3, 3) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(0, 0), Square(3, 3)) shouldBe None
  }

  // ── Knight (Springer) ───────────────────────────────────────────────────────

  "Board.move for a knight" should "allow all 8 L-shaped moves" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.Knight))
    val board = Board(g)
    val targets = List(
      Square(5, 4), Square(5, 2), Square(1, 4), Square(1, 2),
      Square(4, 5), Square(2, 5), Square(4, 1), Square(2, 1)
    )
    targets.foreach(t => board.move(Square(3, 3), t) shouldBe defined)
  }

  it should "jump over pieces" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Knight),
      Square(0, 1) -> Piece(Color.White, PieceKind.Pawn),
      Square(1, 0) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(0, 0), Square(1, 2)) shouldBe defined
  }

  it should "not allow non-L moves" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.Knight))
    Board(g).move(Square(3, 3), Square(3, 5)) shouldBe None
    Board(g).move(Square(3, 3), Square(5, 5)) shouldBe None
  }

  it should "capture an enemy piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Knight),
      Square(1, 2) -> Piece(Color.Black, PieceKind.Pawn)
    )
    val result = Board(g).move(Square(0, 0), Square(1, 2))
    result shouldBe defined
    result.get.pieceAt(Square(1, 2)) shouldBe Some(Piece(Color.White, PieceKind.Knight))
  }

  it should "not capture a friendly piece" in {
    val g = Map(
      Square(0, 0) -> Piece(Color.White, PieceKind.Knight),
      Square(1, 2) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(0, 0), Square(1, 2)) shouldBe None
  }

  // ── Queen (Dame) ─────────────────────────────────────────────────────────────

  "Board.move for a queen" should "allow moving vertically" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.Queen))
    Board(g).move(Square(3, 3), Square(3, 7)) shouldBe defined
  }

  it should "allow moving horizontally" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.Queen))
    Board(g).move(Square(3, 3), Square(7, 3)) shouldBe defined
  }

  it should "allow moving diagonally" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.Queen))
    Board(g).move(Square(3, 3), Square(6, 6)) shouldBe defined
  }

  it should "not allow an irregular move" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.Queen))
    Board(g).move(Square(3, 3), Square(5, 6)) shouldBe None
  }

  it should "not jump over a piece" in {
    val g = Map(
      Square(3, 3) -> Piece(Color.White, PieceKind.Queen),
      Square(3, 5) -> Piece(Color.Black, PieceKind.Pawn)
    )
    Board(g).move(Square(3, 3), Square(3, 7)) shouldBe None
  }

  it should "capture an enemy piece" in {
    val g = Map(
      Square(3, 3) -> Piece(Color.White, PieceKind.Queen),
      Square(3, 6) -> Piece(Color.Black, PieceKind.Pawn)
    )
    Board(g).move(Square(3, 3), Square(3, 6)) shouldBe defined
  }

  it should "not capture a friendly piece" in {
    val g = Map(
      Square(3, 3) -> Piece(Color.White, PieceKind.Queen),
      Square(3, 6) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(3, 3), Square(3, 6)) shouldBe None
  }

  // ── King ────────────────────────────────────────────────────────────────────

  "Board.move for a king" should "allow one step in any direction" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.King))
    val board = Board(g)
    val targets = List(
      Square(3, 4), Square(3, 2), Square(4, 3), Square(2, 3),
      Square(4, 4), Square(2, 4), Square(4, 2), Square(2, 2)
    )
    targets.foreach(t => board.move(Square(3, 3), t) shouldBe defined)
  }

  it should "not allow moving more than one step" in {
    val g = Map(Square(3, 3) -> Piece(Color.White, PieceKind.King))
    Board(g).move(Square(3, 3), Square(3, 5)) shouldBe None
  }

  it should "capture an enemy piece" in {
    val g = Map(
      Square(3, 3) -> Piece(Color.White, PieceKind.King),
      Square(4, 4) -> Piece(Color.Black, PieceKind.Pawn)
    )
    Board(g).move(Square(3, 3), Square(4, 4)) shouldBe defined
  }

  it should "not capture a friendly piece" in {
    val g = Map(
      Square(3, 3) -> Piece(Color.White, PieceKind.King),
      Square(4, 4) -> Piece(Color.White, PieceKind.Pawn)
    )
    Board(g).move(Square(3, 3), Square(4, 4)) shouldBe None
  }
