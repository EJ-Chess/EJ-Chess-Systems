// core/src/main/scala/de/eljachess/chess/controller/GameController.scala
package de.eljachess.chess.controller

import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Square}

case class GameController(
  board:          Board,
  currentTurn:    Color = Color.White,
  halfmoveClock:  Int   = 0,
  fullmoveNumber: Int   = 1
):

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
      case Left(err)                    => (this, err)
      case Right(ParsedMove.FenQuery)   => (this, Fen.encode(this))
      case Right(ParsedMove.FenLoad(s)) =>
        Fen.decode(s) match
          case Right(newCtrl) => (newCtrl, "Position loaded")
          case Left(err)      => (this, err)
      case Right(parsed) =>
        val (from, to, promo) = parsed match
          case ParsedMove.Move(f, t, p)      => (f, t, p)
          case ParsedMove.Castling(kingside) =>
            val row   = if currentTurn == Color.White then 0 else 7
            val toCol = if kingside then 6 else 2
            (Square(4, row), Square(toCol, row), None)
          case _ => throw AssertionError("unreachable: FenQuery/FenLoad handled above")
        board.pieceAt(from) match
          case None =>
            (this, s"No piece at ${from.toAlgebraic}")
          case Some(piece) if piece.color != currentTurn =>
            (this, s"It's ${colorName(currentTurn)}'s turn")
          case Some(_) =>
            val captured = board.pieceAt(to)
            board.move(from, to, promo) match
              case None                                                   => (this, "Invalid move")
              case Some(newBoard) if newBoard.isInCheck(currentTurn)     => (this, "Invalid move")
              case Some(newBoard) =>
                val nextTurn    = if currentTurn == Color.White then Color.Black else Color.White
                val isPawnMove  = board.pieceAt(from).exists(_.kind == PieceKind.Pawn)
                val isCapture   = captured.isDefined
                val newHalfmove = if isPawnMove || isCapture then 0 else halfmoveClock + 1
                val newFullmove = if currentTurn == Color.Black then fullmoveNumber + 1 else fullmoveNumber
                val moveMsg     = s"Moved ${from.toAlgebraic} to ${to.toAlgebraic}"
                val captureStr  = captured.map(p => s" – captured ${colorName(p.color)} ${kindName(p.kind)}").getOrElse("")
                val statusStr   =
                  val inCheck  = newBoard.isInCheck(nextTurn)
                  val hasMoves = newBoard.legalMoves(nextTurn).nonEmpty
                  if inCheck && !hasMoves then " – Checkmate!"
                  else if !inCheck && !hasMoves then " – Stalemate!"
                  else if inCheck then " – Check!"
                  else ""
                (GameController(newBoard, nextTurn, newHalfmove, newFullmove), moveMsg + captureStr + statusStr)
