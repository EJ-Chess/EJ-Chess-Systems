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
    * Exposed in **curried** form so that the board and colour can be partially applied,
    * producing a reusable `String => Either[...]` decoder for a given position:
    *
    * {{{
    *   val decode = SanDecoder.expand(board)(Color.White)
    *   val r1     = decode("e4")
    *   val r2     = decode("Nf3")
    * }}}
    *
    * @param board        the current board position
    * @param currentColor the side to move
    * @param san          a single SAN token, e.g. "e4", "Nf3", "O-O", "exd5", "e8=Q"
    * @return [[Right]] with the triple on success, [[Left]] with a human-readable error otherwise
    */
  def expand(board: Board)(currentColor: Color)(san: String): Either[String, (Square, Square, Option[PieceKind])] =
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
      case SanPattern(piece, file, rank, dest, promo) =>
        parseSquare(dest) match
          // $COVERAGE-OFF$ regex guarantees dest matches [a-h][1-8], so None is unreachable
          case None => Left(s"Invalid destination square: $dest")
          // $COVERAGE-ON$
          case Some(destSquare) =>
            val candidates = legalCandidates(board, currentColor, destSquare,
                                             Option(piece).map(_.head),
                                             Option(file).map(_.head),
                                             Option(rank).map(_.head))
            candidates match
              case Nil             => Left(s"No piece can make move $san")
              case List((from, _)) => Right((from, destSquare, parsePromotion(Option(promo).map(_.head))))
              case _               => Left(s"Move $san is ambiguous")
      case _ =>
        Left(s"Invalid SAN syntax: $san")

  /** Filter all legal moves for `currentColor` to those matching the SAN criteria. */
  private def legalCandidates(
    board: Board,
    currentColor: Color,
    dest: Square,
    piece: Option[Char],
    file: Option[Char],
    rank: Option[Char]
  ): List[(Square, Square)] =
    board.legalMoves(currentColor)
      .filter { case (from, to) =>
        val matchesPiece = piece match
          case None    => board.pieceAt(from).exists(_.kind == PieceKind.Pawn)
          case Some(p) => board.pieceAt(from).exists(_.kind == kindFromChar(p))
        val matchesFile = file.forall(f => from.toAlgebraic(0) == f)
        val matchesRank = rank.forall(r => from.toAlgebraic(1) == r)
        to == dest && matchesPiece && matchesFile && matchesRank
      }

  private def parseSquare(s: String): Option[Square] =
    if s.length == 2 && s(0) >= 'a' && s(0) <= 'h' && s(1) >= '1' && s(1) <= '8'
    then Some(Square(s(0) - 'a', s(1) - '1'))
    // $COVERAGE-OFF$ regex guarantees only valid [a-h][1-8] strings reach parseSquare
    else None
    // $COVERAGE-ON$

  private def parsePromotion(p: Option[Char]): Option[PieceKind] = p match
    case Some('Q') => Some(PieceKind.Queen)
    case Some('R') => Some(PieceKind.Rook)
    case Some('B') => Some(PieceKind.Bishop)
    case Some('N') => Some(PieceKind.Knight)
    case _         => None

  private def kindFromChar(c: Char): PieceKind = c match
    case 'N' => PieceKind.Knight
    case 'B' => PieceKind.Bishop
    case 'R' => PieceKind.Rook
    case 'Q' => PieceKind.Queen
    case 'K' => PieceKind.King
    // $COVERAGE-OFF$ regex only allows NBRQK through to this function
    case _   => throw new IllegalArgumentException(s"Unknown piece char: $c")
    // $COVERAGE-ON$
