// core/src/main/scala/de/eljachess/chess/controller/SanDecoder.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, PieceKind, Square}

/** Converts a SAN (Standard Algebraic Notation) token into an algebraic move triple
  * (fromSquare, toSquare, optionalPromotion).
  *
  * The decoder consults [[Board.legalMoves]] to resolve piece identity and disambiguation.
  * It does **not** execute the move; callers remain responsible for applying it to the board.
  */
object SanDecoder:

  /** Expand a SAN string into `(from, to, promotion)`.
    *
    * @param board        the current board position
    * @param currentColor the side to move
    * @param san          a single SAN token, e.g. "e4", "Nf3", "O-O", "exd5", "e8=Q"
    * @return [[Right]] with the triple on success, [[Left]] with a human-readable error otherwise
    */
  def expand(
    board: Board,
    currentColor: Color,
    san: String
  ): Either[String, (Square, Square, Option[PieceKind])] =
    val normalized = san.trim.replaceAll("[+#]$", "")
    normalized match
      case "O-O" =>
        val row = if currentColor == Color.White then 0 else 7
        Right((Square(4, row), Square(6, row), None))
      case "O-O-O" =>
        val row = if currentColor == Color.White then 0 else 7
        Right((Square(4, row), Square(2, row), None))
      case _ =>
        expandPieceMove(board, currentColor, normalized)

  // SAN grammar (simplified, no en-passant annotation):
  //   [NBRQK]? [a-h]? [1-8]? x? [a-h][1-8] (=[NBRQ])?
  private val SanPattern =
    """^([NBRQK])?([a-h])?([1-8])?x?([a-h][1-8])(?:=([NBRQ]))?$""".r

  private def expandPieceMove(
    board: Board,
    currentColor: Color,
    san: String
  ): Either[String, (Square, Square, Option[PieceKind])] =
    san match
      case SanPattern(pieceChar, fileHint, rankHint, dest, promoChar) =>
        parseSquare(dest) match
          case None =>
            Left(s"Invalid destination square: $dest")
          case Some(destSquare) =>
            val candidates = legalCandidates(board, currentColor, destSquare, pieceChar, fileHint, rankHint)
            candidates match
              case Nil =>
                Left(s"Move $san is illegal: no piece can reach $dest")
              case List((from, _)) =>
                Right((from, destSquare, parsePromotion(promoChar)))
              case _ =>
                Left(s"Move $san is ambiguous")
      case _ =>
        Left(s"Invalid SAN syntax: $san")

  /** Filter all legal moves for `currentColor` to those matching the SAN criteria. */
  private def legalCandidates(
    board: Board,
    currentColor: Color,
    dest: Square,
    pieceChar: String,
    fileHint: String,
    rankHint: String
  ): List[(Square, Square)] =
    board.legalMoves(currentColor)
      .filter { case (from, to) =>
        to == dest &&
        matchesPiece(board, from, pieceChar) &&
        (fileHint == null || from.toAlgebraic.charAt(0) == fileHint.charAt(0)) &&
        (rankHint == null || from.toAlgebraic.charAt(1) == rankHint.charAt(0))
      }

  private def matchesPiece(board: Board, from: Square, pieceChar: String): Boolean =
    board.pieceAt(from).exists { piece =>
      if pieceChar == null then piece.kind == PieceKind.Pawn
      else piece.kind == kindFromChar(pieceChar)
    }

  private def parseSquare(s: String): Option[Square] =
    Option.when(
      s != null && s.length == 2 &&
      s.charAt(0) >= 'a' && s.charAt(0) <= 'h' &&
      s.charAt(1) >= '1' && s.charAt(1) <= '8'
    )(Square(s.charAt(0) - 'a', s.charAt(1) - '1'))

  private def parsePromotion(p: String): Option[PieceKind] = p match
    case "Q" => Some(PieceKind.Queen)
    case "R" => Some(PieceKind.Rook)
    case "B" => Some(PieceKind.Bishop)
    case "N" => Some(PieceKind.Knight)
    case _   => None

  private def kindFromChar(c: String): PieceKind = c match
    case "N" => PieceKind.Knight
    case "B" => PieceKind.Bishop
    case "R" => PieceKind.Rook
    case "Q" => PieceKind.Queen
    case "K" => PieceKind.King
    case _   => PieceKind.Pawn
