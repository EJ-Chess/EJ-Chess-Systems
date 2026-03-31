// core/src/main/scala/de/eljachess/chess/model/Pgn.scala
package de.eljachess.chess.model

import de.eljachess.chess.controller.{GameController, ParsedMove}
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Pgn:

  private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

  def encode(history: List[(GameController, ParsedMove)],
             whiteName: String,
             blackName: String,
             currentPosition: GameController): String =
    val result = detectResult(currentPosition)
    val headers = buildHeaders(whiteName, blackName, result)
    val moveList = buildMoveList(history, currentPosition)
    if moveList.isEmpty then s"$headers\n\n$result"
    else s"$headers\n\n$moveList $result"

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
    val hasMoves = ctrl.board.legalMoves(nextToMove).nonEmpty
    val inCheck = ctrl.board.isInCheck(nextToMove)
    (hasMoves, inCheck) match
      case (false, true)  => if nextToMove == Color.White then "0-1" else "1-0"
      case (false, false) => "1/2-1/2"
      case _              => "*"

  private def buildMoveList(history: List[(GameController, ParsedMove)],
                            currentPosition: GameController): String =
    // Implemented in Task 2
    ""

  def sanForMove(boardBefore: Board,
                 move: ParsedMove,
                 boardAfter: Board): String =
    // Implemented in Task 2
    ""
