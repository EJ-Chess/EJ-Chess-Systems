// core/src/main/scala/de/eljachess/chess/model/Fen.scala
package de.eljachess.chess.model

/** Parses a FEN string into a board snapshot.
  *
  * Returns a minimal state record containing the decoded [[Board]] and the active color.
  * Only the piece-placement and active-color fields are used; en-passant, castling rights,
  * and move counters are accepted but ignored.
  */
object Fen:

  final case class FenState(board: Board, activeColor: Color)

  /** Decode a FEN string such as
    * {{{
    * 4k3/4P3/8/8/8/8/8/4K3 w - - 0 1
    * }}}
    * into a [[FenState]], or return a [[Left]] with an error message.
    */
  def decode(fen: String): Either[String, FenState] =
    val parts = fen.trim.split("\\s+")
    if parts.length < 2 then return Left(s"FEN must have at least 2 fields, got: $fen")

    for
      grid  <- parsePlacement(parts(0))
      color <- parseColor(parts(1))
    yield FenState(Board(grid), color)

  // ── piece placement ────────────────────────────────────────────────────────

  private def parsePlacement(placement: String): Either[String, Map[Square, Piece]] =
    val ranks = placement.split('/')
    if ranks.length != 8 then return Left(s"FEN placement must have 8 ranks, got ${ranks.length}")

    // FEN rank order: rank 8 (row=7) first, rank 1 (row=0) last
    ranks.zipWithIndex.foldLeft[Either[String, Map[Square, Piece]]](Right(Map.empty)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), (rankStr, fenRankIndex)) =>
        val row = 7 - fenRankIndex  // convert FEN rank index to board row
        parseRank(rankStr, row).map(acc ++ _)
    }

  private def parseRank(rankStr: String, row: Int): Either[String, Map[Square, Piece]] =
    rankStr.foldLeft[Either[String, (Int, Map[Square, Piece])]](Right((0, Map.empty))) {
      case (Left(err), _) => Left(err)
      case (Right((col, acc)), ch) =>
        if col > 7 then Left(s"FEN rank '$rankStr' exceeds 8 columns")
        else if ch.isDigit then
          val skip = ch - '0'
          if skip < 1 || skip > 8 then Left(s"Invalid empty-square count: $ch")
          else Right((col + skip, acc))
        else
          charToPiece(ch) match
            case None        => Left(s"Unknown FEN piece character: $ch")
            case Some(piece) => Right((col + 1, acc + (Square(col, row) -> piece)))
    }.flatMap { case (col, m) =>
      if col != 8 then Left(s"FEN rank '$rankStr' covers $col columns instead of 8")
      else Right(m)
    }

  private def charToPiece(ch: Char): Option[Piece] =
    val color = if ch.isUpper then Color.White else Color.Black
    val kind = ch.toLower match
      case 'p' => Some(PieceKind.Pawn)
      case 'n' => Some(PieceKind.Knight)
      case 'b' => Some(PieceKind.Bishop)
      case 'r' => Some(PieceKind.Rook)
      case 'q' => Some(PieceKind.Queen)
      case 'k' => Some(PieceKind.King)
      case _   => None
    kind.map(Piece(color, _))

  // ── active color ───────────────────────────────────────────────────────────

  private def parseColor(s: String): Either[String, Color] = s match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left(s"Invalid active-color field: $s")
