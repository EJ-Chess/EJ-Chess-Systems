// core/src/main/scala/de/eljachess/chess/controller/GameController.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color}

case class GameController(board: Board, currentTurn: Color = Color.White):

  def handleCommand(input: String): (GameController, String) =
    CommandParser.parse(input) match
      case Left(err) => (this, err)
      case Right((from, to)) =>
        board.pieceAt(from) match
          case None =>
            (this, s"No piece at ${from.toAlgebraic}")
          case Some(piece) if piece.color != currentTurn =>
            val whose = if currentTurn == Color.White then "White" else "Black"
            (this, s"It's ${whose}'s turn")
          case Some(_) =>
            board.move(from, to) match
              case None           => (this, "Invalid move")
              case Some(newBoard) =>
                val nextTurn = if currentTurn == Color.White then Color.Black else Color.White
                (GameController(newBoard, nextTurn), s"Moved ${from.toAlgebraic} to ${to.toAlgebraic}")
