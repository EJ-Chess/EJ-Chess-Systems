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
    // Pre-allocate: worst case = 32 pieces + 28 digits + 7 slashes = 71 chars
    val sb = new java.lang.StringBuilder(71)
    var row = 7
    while row >= 0 do
      if row < 7 then sb.append('/')
      var col   = 0
      var empty = 0
      while col < 8 do
        board.pieceAt(Square(col, row)) match
          case None =>
            empty += 1
          case Some(Piece(color, kind)) =>
            if empty > 0 then
              sb.append(('0' + empty).toChar)
              empty = 0
            val base: Char = kind match
              case PieceKind.King   => 'K'
              case PieceKind.Queen  => 'Q'
              case PieceKind.Rook   => 'R'
              case PieceKind.Bishop => 'B'
              case PieceKind.Knight => 'N'
              case PieceKind.Pawn   => 'P'
            sb.append(if color == Color.White then base else base.toLower)
        col += 1
      if empty > 0 then sb.append(('0' + empty).toChar)
      row -= 1
    sb.toString

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
      Left(s"Invalid FEN: expected 8 ranks, got ${ranks.length}")
    else
      val result = scala.collection.mutable.Map[Square, Piece]()

      // Regex to match: piece (uppercase or lowercase letter) OR empty squares (digit 1-8)
      val validTokenPattern = """([pnbrqkPNBRQK]|[1-8])""".r

      for (rank, rankIdx) <- ranks.zipWithIndex do
        val row = 7 - rankIdx
        var col = 0

        // First, check for invalid characters by counting valid tokens
        val validTokens = validTokenPattern.findAllMatchIn(rank).map(_.matched).toList
        val validCharsCount = validTokens.map(t => if t(0).isDigit then t(0).asDigit else 1).sum

        // Check if there are invalid characters (non-matching characters in the rank)
        if validTokens.map(_.length).sum != rank.length then
          // Find the first invalid character
          for ch <- rank if !ch.isDigit && !"pnbrqkPNBRQK".contains(ch) do
            return Left(s"Invalid FEN: invalid piece char '$ch'")

        for token <- validTokens do
          if token(0).isDigit then
            col += token(0).asDigit
          else
            val ch = token(0)
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
                result(Square(col, row)) = Piece(color, k)
                col += 1

        if col != 8 then return Left(s"Invalid FEN: rank ${rankIdx + 1} has wrong length")

      Right(result.toMap)

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
