// core/src/main/scala/de/eljachess/chess/model/Fen.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.GameController

object Fen:

  // ── Encode ─────────────────────────────────────────────────────────────────

  def encode(ctrl: GameController): String =
    val board    = ctrl.board
    val color    = if ctrl.currentTurn == Color.White then "w" else "b"
    val castling = encodeCastling(board.castlingRights)
    val ep       = board.enPassantTarget.map(_.toAlgebraic).getOrElse("-")
    s"${encodePlacement(board)} $color $castling $ep ${ctrl.halfmoveClock} ${ctrl.fullmoveNumber}"

  private def encodePlacement(board: Board): String =
    (7 to 0 by -1).map { row =>
      val (rankStr, emptyCount) =
        (0 to 7).foldLeft(("", 0)) { case ((acc, empty), col) =>
          board.pieceAt(Square(col, row)) match
            case None =>
              (acc, empty + 1)
            case Some(piece) =>
              val prefix = if empty > 0 then acc + empty.toString else acc
              (prefix + pieceChar(piece), 0)
        }
      if emptyCount > 0 then rankStr + emptyCount.toString else rankStr
    }.mkString("/")

  private def pieceChar(piece: Piece): String =
    val c = piece.kind match
      case PieceKind.King   => 'K'
      case PieceKind.Queen  => 'Q'
      case PieceKind.Rook   => 'R'
      case PieceKind.Bishop => 'B'
      case PieceKind.Knight => 'N'
      case PieceKind.Pawn   => 'P'
    if piece.color == Color.White then c.toString else c.toLower.toString

  private def encodeCastling(rights: CastlingRights): String =
    val s = (if rights.whiteKingside  then "K" else "") +
            (if rights.whiteQueenside then "Q" else "") +
            (if rights.blackKingside  then "k" else "") +
            (if rights.blackQueenside then "q" else "")
    if s.isEmpty then "-" else s

  // ── Decode placeholder (Task 2) ────────────────────────────────────────────
  def decode(fen: String): Either[String, GameController] =
    Left("Not implemented")
