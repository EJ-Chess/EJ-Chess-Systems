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

  // ── isInCheck ────────────────────────────────────────────────────────────────

  "Board.isInCheck" should "return true when the king is attacked" in {
    // White king on e1, black rook on e5 – rook attacks along the e-file
    val g = Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(4, 4) -> Piece(Color.Black, PieceKind.Rook)
    )
    Board(g).isInCheck(Color.White) shouldBe true
  }

  it should "return false on the initial board" in {
    Board.initial.isInCheck(Color.White) shouldBe false
    Board.initial.isInCheck(Color.Black) shouldBe false
  }

  // ── legalMoves ───────────────────────────────────────────────────────────────

  "Board.legalMoves" should "be empty in a checkmate position" in {
    // Black king a8, white queen b6, white rook c8 → checkmate
    val g = Map(
      Square(1, 5) -> Piece(Color.White, PieceKind.Queen),
      Square(2, 7) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 7) -> Piece(Color.Black, PieceKind.King)
    )
    Board(g).legalMoves(Color.Black) shouldBe empty
  }

  it should "be empty in a stalemate position" in {
    // Black king a8, white queen c7 – king not in check but all escape squares covered
    val g = Map(
      Square(2, 6) -> Piece(Color.White, PieceKind.Queen),
      Square(0, 7) -> Piece(Color.Black, PieceKind.King)
    )
    Board(g).legalMoves(Color.Black) shouldBe empty
  }

  it should "not include moves that leave the own king in check" in {
    // White king e1, white rook e4 (pinned by black rook e8) – rook may only move along the e-file
    val g = Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(4, 3) -> Piece(Color.White, PieceKind.Rook),
      Square(4, 7) -> Piece(Color.Black, PieceKind.Rook)
    )
    val rookMoves = Board(g).legalMoves(Color.White).filter(_._1 == Square(4, 3))
    rookMoves should not be empty                              // pinned rook can still move along the e-file
    rookMoves.foreach { case (_, to) => to.col shouldBe 4 }   // but only along e-file (col 4)
  }

  // ── Board fields ─────────────────────────────────────────────────────────

  "Board.initial" should "have all castling rights enabled" in {
    Board.initial.castlingRights shouldBe CastlingRights()
  }

  it should "have no en passant target" in {
    Board.initial.enPassantTarget shouldBe None
  }

  // ── Castling ─────────────────────────────────────────────────────────────

  private def castlingBoard(color: Color, kingside: Boolean): Board =
    val row      = if color == Color.White then 0 else 7
    val rookCol  = if kingside then 7 else 0
    Board(Map(
      Square(4, row) -> Piece(color, PieceKind.King),
      Square(rookCol, row) -> Piece(color, PieceKind.Rook)
    ))

  "Board.move for castling" should "allow white kingside castling" in {
    val b = castlingBoard(Color.White, kingside = true)
    val result = b.move(Square(4, 0), Square(6, 0))
    result shouldBe defined
    result.get.pieceAt(Square(6, 0)) shouldBe Some(Piece(Color.White, PieceKind.King))
    result.get.pieceAt(Square(5, 0)) shouldBe Some(Piece(Color.White, PieceKind.Rook))
    result.get.pieceAt(Square(4, 0)) shouldBe None
    result.get.pieceAt(Square(7, 0)) shouldBe None
  }

  it should "allow white queenside castling" in {
    val b = castlingBoard(Color.White, kingside = false)
    val result = b.move(Square(4, 0), Square(2, 0))
    result shouldBe defined
    result.get.pieceAt(Square(2, 0)) shouldBe Some(Piece(Color.White, PieceKind.King))
    result.get.pieceAt(Square(3, 0)) shouldBe Some(Piece(Color.White, PieceKind.Rook))
  }

  it should "allow black kingside castling" in {
    val b = castlingBoard(Color.Black, kingside = true)
    val result = b.move(Square(4, 7), Square(6, 7))
    result shouldBe defined
    result.get.pieceAt(Square(6, 7)) shouldBe Some(Piece(Color.Black, PieceKind.King))
    result.get.pieceAt(Square(5, 7)) shouldBe Some(Piece(Color.Black, PieceKind.Rook))
  }

  it should "allow black queenside castling" in {
    val b = castlingBoard(Color.Black, kingside = false)
    val result = b.move(Square(4, 7), Square(2, 7))
    result shouldBe defined
  }

  it should "revoke kingside right after king moves" in {
    val b = castlingBoard(Color.White, kingside = true)
    val b2 = b.move(Square(4, 0), Square(5, 0)).get  // king moves one step
    b2.castlingRights.whiteKingside  shouldBe false
    b2.castlingRights.whiteQueenside shouldBe false
  }

  it should "revoke queenside right after a-rook moves" in {
    val b = Board(Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(0, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(7, 0) -> Piece(Color.White, PieceKind.Rook)
    ))
    val b2 = b.move(Square(0, 0), Square(0, 1)).get
    b2.castlingRights.whiteQueenside shouldBe false
    b2.castlingRights.whiteKingside  shouldBe true
  }

  it should "revoke castling right when rook is captured on home square" in {
    val b = Board(Map(
      Square(4, 7) -> Piece(Color.Black, PieceKind.King),
      Square(7, 7) -> Piece(Color.Black, PieceKind.Rook),
      Square(6, 5) -> Piece(Color.White, PieceKind.Knight)
    ))
    val b2 = b.move(Square(6, 5), Square(7, 7)).get
    b2.castlingRights.blackKingside  shouldBe false
    b2.castlingRights.blackQueenside shouldBe true
  }

  it should "not allow castling when right is revoked" in {
    val b = castlingBoard(Color.White, kingside = true)
      .copy(castlingRights = CastlingRights(whiteKingside = false))
    b.move(Square(4, 0), Square(6, 0)) shouldBe None
  }

  it should "not allow castling when path is blocked" in {
    val b = Board(Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(7, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(5, 0) -> Piece(Color.White, PieceKind.Bishop)
    ))
    b.move(Square(4, 0), Square(6, 0)) shouldBe None
  }

  it should "not allow castling when king is in check" in {
    val b = Board(Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(7, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(4, 7) -> Piece(Color.Black, PieceKind.Rook)
    ))
    b.move(Square(4, 0), Square(6, 0)) shouldBe None
  }

  it should "not allow castling through an attacked square" in {
    val b = Board(Map(
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(7, 0) -> Piece(Color.White, PieceKind.Rook),
      Square(5, 7) -> Piece(Color.Black, PieceKind.Rook)
    ))
    b.move(Square(4, 0), Square(6, 0)) shouldBe None
  }

  // ── En Passant ────────────────────────────────────────────────────────────

  "Board.move for en passant" should "set enPassantTarget after a 2-square pawn advance" in {
    val result = Board.initial.move(Square(4, 1), Square(4, 3))  // e2-e4
    result shouldBe defined
    result.get.enPassantTarget shouldBe Some(Square(4, 2))       // e3
  }

  it should "clear enPassantTarget after any other move" in {
    val b = Board.initial.move(Square(4, 1), Square(4, 3)).get   // e2-e4, ep=e3
    val b2 = b.move(Square(0, 6), Square(0, 5)).get              // a7-a6, ep cleared
    b2.enPassantTarget shouldBe None
  }

  it should "allow white pawn to capture en passant" in {
    val b = Board(Map(
      Square(4, 4) -> Piece(Color.White, PieceKind.Pawn),
      Square(3, 4) -> Piece(Color.Black, PieceKind.Pawn)
    ), enPassantTarget = Some(Square(3, 5)))
    val result = b.move(Square(4, 4), Square(3, 5))  // exd6 en passant
    result shouldBe defined
    result.get.pieceAt(Square(3, 5)) shouldBe Some(Piece(Color.White, PieceKind.Pawn))
    result.get.pieceAt(Square(3, 4)) shouldBe None   // captured pawn removed
    result.get.pieceAt(Square(4, 4)) shouldBe None
  }

  it should "allow black pawn to capture en passant" in {
    val b = Board(Map(
      Square(3, 3) -> Piece(Color.Black, PieceKind.Pawn),
      Square(4, 3) -> Piece(Color.White, PieceKind.Pawn)
    ), enPassantTarget = Some(Square(4, 2)))
    val result = b.move(Square(3, 3), Square(4, 2))  // dxe3 en passant
    result shouldBe defined
    result.get.pieceAt(Square(4, 2)) shouldBe Some(Piece(Color.Black, PieceKind.Pawn))
    result.get.pieceAt(Square(4, 3)) shouldBe None   // captured pawn removed
  }

  it should "not allow en passant capture after the window has passed" in {
    val b = Board(Map(
      Square(4, 4) -> Piece(Color.White, PieceKind.Pawn),
      Square(3, 4) -> Piece(Color.Black, PieceKind.Pawn),
      Square(0, 7) -> Piece(Color.Black, PieceKind.Rook)
    ), enPassantTarget = Some(Square(3, 5)))
    val b2 = b.move(Square(0, 7), Square(0, 6)).get  // Black plays a different move
    b2.enPassantTarget shouldBe None
    b2.move(Square(4, 4), Square(3, 5)) shouldBe None
  }

  // ── Promotion ─────────────────────────────────────────────────────────────

  "Board.move for promotion" should "promote white pawn to Queen" in {
    val b = Board(Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)))
    val result = b.move(Square(4, 6), Square(4, 7), Some(PieceKind.Queen))
    result shouldBe defined
    result.get.pieceAt(Square(4, 7)) shouldBe Some(Piece(Color.White, PieceKind.Queen))
    result.get.pieceAt(Square(4, 6)) shouldBe None
  }

  it should "promote white pawn to Rook" in {
    val b = Board(Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)))
    b.move(Square(4, 6), Square(4, 7), Some(PieceKind.Rook)).flatMap(_.pieceAt(Square(4, 7))) shouldBe
      Some(Piece(Color.White, PieceKind.Rook))
  }

  it should "promote white pawn to Bishop" in {
    val b = Board(Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)))
    b.move(Square(4, 6), Square(4, 7), Some(PieceKind.Bishop)).flatMap(_.pieceAt(Square(4, 7))) shouldBe
      Some(Piece(Color.White, PieceKind.Bishop))
  }

  it should "promote white pawn to Knight" in {
    val b = Board(Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)))
    b.move(Square(4, 6), Square(4, 7), Some(PieceKind.Knight)).flatMap(_.pieceAt(Square(4, 7))) shouldBe
      Some(Piece(Color.White, PieceKind.Knight))
  }

  it should "promote black pawn to Queen" in {
    val b = Board(Map(Square(4, 1) -> Piece(Color.Black, PieceKind.Pawn)))
    val result = b.move(Square(4, 1), Square(4, 0), Some(PieceKind.Queen))
    result shouldBe defined
    result.get.pieceAt(Square(4, 0)) shouldBe Some(Piece(Color.Black, PieceKind.Queen))
  }

  it should "return None when pawn reaches back rank without promotion piece" in {
    val b = Board(Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)))
    b.move(Square(4, 6), Square(4, 7)) shouldBe None
  }

  it should "allow promotion via diagonal capture" in {
    val b = Board(Map(
      Square(4, 6) -> Piece(Color.White, PieceKind.Pawn),
      Square(5, 7) -> Piece(Color.Black, PieceKind.Rook)
    ))
    val result = b.move(Square(4, 6), Square(5, 7), Some(PieceKind.Queen))
    result shouldBe defined
    result.get.pieceAt(Square(5, 7)) shouldBe Some(Piece(Color.White, PieceKind.Queen))
  }

  "Board.legalMoves" should "include promotion destination squares for pawns near back rank" in {
    val b = Board(Map(Square(4, 6) -> Piece(Color.White, PieceKind.Pawn)))
    val dests = b.legalMoves(Color.White).map(_._2)
    dests should contain (Square(4, 7))
  }
