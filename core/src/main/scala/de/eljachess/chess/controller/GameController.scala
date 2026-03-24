// core/src/main/scala/de/eljachess/chess/controller/GameController.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, PieceKind}

case class GameController(board: Board, currentTurn: Color = Color.White):

  private def colorName(c: Color): String = if c == Color.White then "White" else "Black"
  private def kindName(k: PieceKind): String = k match
    case PieceKind.Pawn   => "Pawn"
    case PieceKind.Rook   => "Rook"
    case PieceKind.Knight => "Knight"
    case PieceKind.Bishop => "Bishop"
    case PieceKind.Queen  => "Queen"
    case PieceKind.King   => "King"


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
            val captured = board.pieceAt(to)
            board.move(from, to) match
              case None                                    => (this, "Invalid move")
              case Some(newBoard) if newBoard.isInCheck(currentTurn) => (this, "Invalid move")
              case Some(newBoard) =>
                val nextTurn   = if currentTurn == Color.White then Color.Black else Color.White
                val moveMsg    = s"Moved ${from.toAlgebraic} to ${to.toAlgebraic}"
                val captureStr = captured.map(p => s" – captured ${colorName(p.color)} ${kindName(p.kind)}").getOrElse("")
                val statusStr  =
                  val inCheck  = newBoard.isInCheck(nextTurn)
                  val hasMoves = newBoard.legalMoves(nextTurn).nonEmpty
                  if inCheck && !hasMoves then " – Checkmate!"
                  else if !inCheck && !hasMoves then " – Stalemate!"
                  else if inCheck then " – Check!"
                  else ""
                (GameController(newBoard, nextTurn), moveMsg + captureStr + statusStr)
