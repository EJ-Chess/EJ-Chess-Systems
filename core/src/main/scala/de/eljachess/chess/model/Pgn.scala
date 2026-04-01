// core/src/main/scala/de/eljachess/chess/model/Pgn.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, ParsedMove}
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Pgn:

  // ── Decode ─────────────────────────────────────────────────────────────────

  def decode(text: String): Either[String, (Map[String, String], List[String])] =
    val lines                    = text.split("\r?\n").toList
    val (headerLines, restLines) = lines.span(l => l.trim.startsWith("[") || l.trim.isEmpty)
    val nonEmptyHeaders          = headerLines.filter(_.trim.nonEmpty)
    parseHeaders(nonEmptyHeaders).map { headers =>
      (headers, parseMoves(restLines.mkString(" ")))
    }

  private def parseHeaders(lines: List[String]): Either[String, Map[String, String]] =
    val tagRegex = """^\[(\w+)\s+"(.*)"\]$""".r
    lines.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), line) =>
        line.trim match
          case tagRegex(tag, value) =>
            if acc.contains(tag) then Left(s"Duplicate PGN tag: $tag")
            else Right(acc + (tag -> value))
          case other => Left(s"Invalid PGN header: $other")
    }

  private def parseMoves(text: String): List[String] =
    val noComments    = text.replaceAll("""\{[^}]*\}""", "")
    val noAnnotations = noComments.replaceAll("[!?]+", "")
    val tokens        = noAnnotations.split("\\s+").toList.filter(_.nonEmpty)
    val moveNumbers   = """^\d+\.+$""".r
    val results       = Set("*", "1-0", "0-1", "1/2-1/2")
    tokens.filterNot(t => moveNumbers.matches(t) || results.contains(t))

  // ── Encode ─────────────────────────────────────────────────────────────────

  private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

  def encode(history: List[(GameController, ParsedMove)],
             whiteName: String,
             blackName: String,
             currentPosition: GameController): String =
    val result   = detectResult(currentPosition)
    val headers  = buildHeaders(whiteName, blackName, result)
    val moveList = buildMoveList(history, currentPosition)
    if moveList.isEmpty then s"$headers\n\n$result"
    else s"$headers\n\n$moveList $result"

  def sanForMove(boardBefore: Board,
                 move: ParsedMove,
                 boardAfter: Board): String = move match
    case ParsedMove.Move(from, to, promotion) =>
      sanForPieceMove(boardBefore, from, to, promotion, boardAfter)
    case ParsedMove.Castling(kingside) =>
      if kingside then "O-O" else "O-O-O"
    case _ =>
      throw new IllegalArgumentException(s"Non-move command in PGN history: $move")

  private def buildHeaders(whiteName: String,
                            blackName: String,
                            result: String): String =
    val today = LocalDate.now().format(formatter)
    s"""[Event "?"]
       |[Site "?"]
       |[Date "$today"]
       |[Round "?"]
       |[White "$whiteName"]
       |[Black "$blackName"]
       |[Result "$result"]""".stripMargin

  private def detectResult(ctrl: GameController): String =
    val nextToMove = ctrl.currentTurn
    val hasMoves   = ctrl.board.legalMoves(nextToMove).nonEmpty
    val inCheck    = ctrl.board.isInCheck(nextToMove)
    (hasMoves, inCheck) match
      case (false, true)  => if nextToMove == Color.White then "0-1" else "1-0"
      case (false, false) => "1/2-1/2"
      case _              => "*"

  private def buildMoveList(history: List[(GameController, ParsedMove)],
                             currentPosition: GameController): String =
    if history.isEmpty then ""
    else
      history.zipWithIndex.map { case ((ctrlBefore, move), i) =>
        val ctrlAfter = if i + 1 < history.size then history(i + 1)._1 else currentPosition
        val san       = sanForMove(ctrlBefore.board, move, ctrlAfter.board)
        if i % 2 == 0 then s"${(i / 2) + 1}. $san" else san
      }.mkString(" ")

  private def sanForPieceMove(boardBefore: Board,
                               from: Square,
                               to: Square,
                               promotion: Option[PieceKind],
                               boardAfter: Board): String =
    val piece       = boardBefore.pieceAt(from)
                        .getOrElse(throw new IllegalArgumentException(s"No piece at ${from.toAlgebraic} in PGN history"))
    val movingColor = piece.color
    val nextColor   = opposite(movingColor)
    val isCapture   = boardBefore.pieceAt(to).isDefined || boardBefore.enPassantTarget.contains(to)

    val moveStr =
      if piece.kind == PieceKind.Pawn then
        if isCapture then s"${from.toAlgebraic.head}x${to.toAlgebraic}" else to.toAlgebraic
      else
        val pieceStr = piece.kind match
          case PieceKind.Knight => "N"
          case PieceKind.Bishop => "B"
          case PieceKind.Rook   => "R"
          case PieceKind.Queen  => "Q"
          case PieceKind.King   => "K"
          case PieceKind.Pawn   =>
            throw new IllegalArgumentException("Pawn reached non-pawn SAN handler")
        val ambiguous = boardBefore.legalMoves(movingColor)
          .filter { case (f, t) =>
            t == to && f != from &&
            boardBefore.pieceAt(f).exists(p => p.kind == piece.kind && p.color == movingColor)
          }
        val disambiguation =
          if ambiguous.isEmpty then ""
          else
            val sameFile = ambiguous.exists(_._1.col == from.col)
            val sameRank = ambiguous.exists(_._1.row == from.row)
            if !sameFile then from.toAlgebraic.head.toString
            else if !sameRank then from.toAlgebraic.last.toString
            else from.toAlgebraic
        val captureStr = if isCapture then "x" else ""
        s"$pieceStr$disambiguation$captureStr${to.toAlgebraic}"

    val promStr  = promotion.map(k => s"=${promotionChar(k)}").getOrElse("")
    val checkStr =
      val inCheck  = boardAfter.isInCheck(nextColor)
      val hasMoves = boardAfter.legalMoves(nextColor).nonEmpty
      if inCheck && !hasMoves then "#"
      else if inCheck then "+"
      else ""

    s"$moveStr$promStr$checkStr"

  private def opposite(color: Color): Color =
    if color == Color.White then Color.Black else Color.White

  // Used only for promotion — Pawn and King are not valid promotion targets
  private def promotionChar(kind: PieceKind): String = kind match
    case PieceKind.Queen  => "Q"
    case PieceKind.Rook   => "R"
    case PieceKind.Bishop => "B"
    case PieceKind.Knight => "N"
    case _                =>
      throw new IllegalArgumentException(s"Invalid promotion piece: $kind")
