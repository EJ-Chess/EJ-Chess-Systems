// core/src/main/scala/de/eljachess/chess/model/Pgn.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, ParsedMove}
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Pgn:

  def encode(history: List[(GameController, ParsedMove)],
             whiteName: String,
             blackName: String,
             currentPosition: GameController): String =
    val headers  = buildHeaders(whiteName, blackName, currentPosition)
    val moveList = buildMoveList(history, currentPosition)
    val result   = detectResult(currentPosition)
    val body     = if moveList.isEmpty then result else s"$moveList $result"
    s"$headers\n\n$body"

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
                            currentPosition: GameController): String =
    val today  = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    val result = detectResult(currentPosition)
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
    if !hasMoves && inCheck then
      if nextToMove == Color.White then "0-1" else "1-0"
    else if !hasMoves then
      "1/2-1/2"
    else
      "*"

  private def buildMoveList(history: List[(GameController, ParsedMove)],
                             currentPosition: GameController): String =
    if history.isEmpty then ""
    else
      val moveStrings = scala.collection.mutable.ListBuffer.empty[String]
      for i <- history.indices do
        val (ctrlBefore, move) = history(i)
        val ctrlAfter          = if i + 1 < history.length then history(i + 1)._1 else currentPosition
        val san                = sanForMove(ctrlBefore.board, move, ctrlAfter.board)
        if i % 2 == 0 then
          val moveNum = (i / 2) + 1
          moveStrings += s"$moveNum. $san"
        else
          moveStrings += san
      moveStrings.mkString(" ")

  private def sanForPieceMove(boardBefore: Board,
                               from: Square,
                               to: Square,
                               promotion: Option[PieceKind],
                               boardAfter: Board): String =
    val piece        = boardBefore.pieceAt(from).get
    val movingColor  = piece.color
    val nextColor    = opposite(movingColor)
    // En passant: pawn moves diagonally to empty square
    val isPawnCapture = piece.kind == PieceKind.Pawn && from.col != to.col
    val isCapture     = boardBefore.pieceAt(to).isDefined || isPawnCapture

    val moveStr =
      if piece.kind == PieceKind.Pawn then
        if isCapture then s"${from.toAlgebraic.head}x${to.toAlgebraic}"
        else to.toAlgebraic
      else
        val pc = pieceChar(piece.kind)
        // Disambiguation: other pieces of same kind/color that can also reach `to`
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
            if !sameFile then from.toAlgebraic.head.toString   // file letter suffices
            else if !sameRank then (from.row + 1).toString      // rank number suffices
            else from.toAlgebraic                               // need full square
        val captureStr = if isCapture then "x" else ""
        s"$pc$disambiguation$captureStr${to.toAlgebraic}"

    val promStr  = promotion.map(k => s"=${pieceChar(k)}").getOrElse("")
    val checkStr =
      val inCheck  = boardAfter.isInCheck(nextColor)
      val hasMoves = boardAfter.legalMoves(nextColor).nonEmpty
      if inCheck && !hasMoves then "#"
      else if inCheck then "+"
      else ""

    s"$moveStr$promStr$checkStr"

  private def opposite(color: Color): Color =
    if color == Color.White then Color.Black else Color.White

  private def pieceChar(kind: PieceKind): String = kind match
    case PieceKind.Knight => "N"
    case PieceKind.Bishop => "B"
    case PieceKind.Rook   => "R"
    case PieceKind.Queen  => "Q"
    case PieceKind.King   => "K"
    case _                => "" // $COVERAGE-OFF$ — Pawn is never passed to pieceChar
