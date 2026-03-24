// core/src/main/scala/de/nowchess/chess/controller/GameController.scala
package de.nowchess.chess.controller

import de.nowchess.chess.model.Board

case class GameController(board: Board):

  def handleCommand(input: String): (GameController, String) =
    CommandParser.parse(input) match
      case Left(err) => (this, err)
      case Right((from, to)) =>
        board.move(from, to) match
          case None           => (this, s"No piece at ${from.toAlgebraic}")
          case Some(newBoard) => (GameController(newBoard), s"Moved ${from.toAlgebraic} to ${to.toAlgebraic}")
