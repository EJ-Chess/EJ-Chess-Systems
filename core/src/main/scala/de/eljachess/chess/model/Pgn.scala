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
    val headers = buildHeaders(whiteName, blackName, currentPosition)
    val moveList = buildMoveList(history, currentPosition)
    val result = detectResult(currentPosition)
    if moveList.isEmpty then s"$headers\n\n$result"
    else s"$headers\n\n$moveList $result"

  private def buildHeaders(whiteName: String,
                           blackName: String,
                           currentPosition: GameController): String =
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
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
    val hasMoves = ctrl.board.legalMoves(nextToMove).nonEmpty
    val inCheck = ctrl.board.isInCheck(nextToMove)
    if !hasMoves && inCheck then
      if nextToMove == Color.White then "0-1" else "1-0"
    else if !hasMoves then
      "1/2-1/2"
    else
      "*"

  private def buildMoveList(history: List[(GameController, ParsedMove)],
                            currentPosition: GameController): String =
    // Implemented in Task 2
    ""

  def sanForMove(boardBefore: Board,
                 move: ParsedMove,
                 boardAfter: Board): String =
    // Implemented in Task 2
    ""
