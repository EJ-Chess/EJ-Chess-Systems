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
    val sb = new StringBuilder(80)
    (7 to 0 by -1).foreach { row =>
      if row < 7 then sb.append('/')
      var emptyCount = 0
      (0 to 7).foreach { col =>
        board.pieceAt(Square(col, row)) match
          case None =>
            emptyCount += 1
          case Some(piece) =>
            if emptyCount > 0 then
              sb.append(emptyCount)
              emptyCount = 0
            sb.append(pieceChar(piece))
      }
      if emptyCount > 0 then sb.append(emptyCount)
    }
    sb.toString()

  private def pieceChar(piece: Piece): Char =
    val c = piece.kind match
      case PieceKind.King   => 'K'
      case PieceKind.Queen  => 'Q'
      case PieceKind.Rook   => 'R'
      case PieceKind.Bishop => 'B'
      case PieceKind.Knight => 'N'
      case PieceKind.Pawn   => 'P'
    if piece.color == Color.White then c else c.toLower

  private def encodeCastling(rights: CastlingRights): String =
    val s = (if rights.whiteKingside  then "K" else "") +
            (if rights.whiteQueenside then "Q" else "") +
            (if rights.blackKingside  then "k" else "") +
            (if rights.blackQueenside then "q" else "")
    if s.isEmpty then "-" else s

  // ── Decode ─────────────────────────────────────────────────────────────────

  def decode(fen: String): Either[String, GameController] =
    val fields = fen.trim.split("\\s+")
    if fields.length != 6 then
      return Left(s"Invalid FEN: expected 6 fields, got ${fields.length}")
    for
      grid            <- parsePlacement(fields(0))
      currentTurn     <- parseColor(fields(1))
      castlingRights  <- parseCastling(fields(2))
      enPassantTarget <- parseEnPassant(fields(3))
      halfmoveClock   <- parseHalfmove(fields(4))
      fullmoveNumber  <- parseFullmove(fields(5))
    yield
      val board = Board(grid, castlingRights, enPassantTarget)
      GameController(board, currentTurn, halfmoveClock, fullmoveNumber)

  private def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
    val ranks = s.split("/", -1)
    if ranks.length != 8 then
      return Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")

    val grid = Array.fill(64)(Option.empty[Piece])

    for (rank, rankIdx) <- ranks.zipWithIndex do
      val row = 7 - rankIdx
      var col = 0
      for ch <- rank do
        if ch.isDigit then
          col += ch.asDigit
        else
          val kindOpt = ch.toLower match
            case 'k' => Some(PieceKind.King)
            case 'q' => Some(PieceKind.Queen)
            case 'r' => Some(PieceKind.Rook)
            case 'b' => Some(PieceKind.Bishop)
            case 'n' => Some(PieceKind.Knight)
            case 'p' => Some(PieceKind.Pawn)
            case _   => None
          kindOpt match
            case None => return Left(s"Invalid FEN: invalid piece char '$ch'")
            case Some(k) =>
              val color = if ch.isUpper then Color.White else Color.Black
              grid(row * 8 + col) = Some(Piece(color, k))
              col += 1
      if col != 8 then return Left(s"Invalid FEN: rank ${rankIdx + 1} has wrong length")

    Right(
      (0 until 64)
        .collect { case idx if grid(idx).nonEmpty => (Square(idx % 8, idx / 8), grid(idx).get) }
        .toMap
    )

  private def parseColor(s: String): Either[String, Color] = s match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left(s"Invalid FEN: invalid active color '$s'")

  private def parseCastling(s: String): Either[String, CastlingRights] =
    if s == "-" then return Right(CastlingRights(false, false, false, false))
    if s.exists(c => !"KQkq".contains(c)) then
      return Left(s"Invalid FEN: invalid castling '$s'")
    Right(CastlingRights(
      whiteKingside  = s.contains('K'),
      whiteQueenside = s.contains('Q'),
      blackKingside  = s.contains('k'),
      blackQueenside = s.contains('q')
    ))

  private def parseEnPassant(s: String): Either[String, Option[Square]] =
    if s == "-" then Right(None)
    else if s.length == 2 && s(0) >= 'a' && s(0) <= 'h' && s(1) >= '1' && s(1) <= '8' then
      Right(Some(Square(s(0) - 'a', s(1) - '1')))
    else
      Left(s"Invalid FEN: invalid en passant square '$s'")

  private def parseHalfmove(s: String): Either[String, Int] =
    s.toIntOption match
      case Some(n) if n >= 0 => Right(n)
      case _                 => Left(s"Invalid FEN: invalid halfmove clock '$s'")

  private def parseFullmove(s: String): Either[String, Int] =
    s.toIntOption match
      case Some(n) if n >= 1 => Right(n)
      case _                 => Left(s"Invalid FEN: invalid fullmove number '$s'")
