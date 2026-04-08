// core/src/main/scala/de/eljachess/chess/model/ParserCombinatorsFEN.scala
package de.eljachess.chess.model

import cats.parse.Parser
import scala.collection.mutable

object ParserCombinatorsFEN:

  /**
   * Parse FEN placement string using pure parser combinators.
   * Example: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
   */
  def parsePlacement(s: String): Either[String, Map[Square, Piece]] =
    placementParser.parseAll(s) match
      case Right(pieces) => Right(pieces.toMap)
      case Left(error)   => Left(s"Invalid FEN: ${error.toString()}")

  /**
   * Parser for a single piece character: [pnbrqkPNBRQK]
   * Returns (Color, PieceKind).
   */
  private val pieceParser: Parser[(Color, PieceKind)] =
    val whitePieces = Parser.charIn("KQRBNP").map { ch =>
      (Color.White, ch.toLower match
        case 'k' => PieceKind.King
        case 'q' => PieceKind.Queen
        case 'r' => PieceKind.Rook
        case 'b' => PieceKind.Bishop
        case 'n' => PieceKind.Knight
        case _   => PieceKind.Pawn)
    }
    val blackPieces = Parser.charIn("kqrbnp").map { ch =>
      (Color.Black, ch match
        case 'k' => PieceKind.King
        case 'q' => PieceKind.Queen
        case 'r' => PieceKind.Rook
        case 'b' => PieceKind.Bishop
        case 'n' => PieceKind.Knight
        case _   => PieceKind.Pawn)
    }
    whitePieces | blackPieces

  /**
   * Parser for empty squares: [1-8]
   * Returns the number of empty squares.
   */
  private val digitParser: Parser[Int] =
    Parser.charIn('1' to '8').map(_.asDigit)

  /**
   * A rank token: either a digit (empty count) or a piece.
   */
  private val rankTokenParser: Parser[Either[Int, (Color, PieceKind)]] =
    digitParser.map(Left(_)) | pieceParser.map(Right(_))

  /**
   * A complete rank string (e.g., "rnbqkbnr").
   * Returns list of (col, color, pieceKind) tuples where col is 0..7.
   * Validates that the column count reaches exactly 8.
   */
  private val rankParser: Parser[List[(Int, Color, PieceKind)]] =
    rankTokenParser.rep.map { tokens =>
      val pieces = mutable.ListBuffer[(Int, Color, PieceKind)]()
      var col = 0
      for token <- tokens.toList do
        token match
          case Left(emptyCount) =>
            col += emptyCount
          case Right((color, kind)) =>
            if col >= 8 then
              sys.error(s"Rank exceeds 8 squares (col=$col)")
            pieces += ((col, color, kind))
            col += 1
      if col != 8 then
        sys.error(s"Rank has $col squares, expected 8")
      pieces.toList
    }

  /**
   * Parser for 8 ranks separated by "/", yielding all pieces with their squares.
   * Row mapping: rank 8 (index 0) maps to row 7, rank 1 (index 7) maps to row 0.
   */
  private val placementParser: Parser[List[(Square, Piece)]] =
    val rankSeparator = Parser.char('/')
    rankParser.repSep(8, 8, rankSeparator).map { ranks =>
      val pieces = mutable.ListBuffer[(Square, Piece)]()
      for (rank, rankIdx) <- ranks.toList.zipWithIndex do
        val row = 7 - rankIdx
        for (col, color, kind) <- rank do
          pieces += (Square(col, row) -> Piece(color, kind))
      pieces.toList
    }
