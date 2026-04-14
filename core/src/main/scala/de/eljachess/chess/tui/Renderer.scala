// core/src/main/scala/de/eljachess/chess/tui/Renderer.scala
package de.eljachess.chess.tui

import de.eljachess.chess.model.{Board, Color, Piece, PieceKind, Square}

object Renderer:

  private val Reset   = "\u001b[0m"
  private val LightBg = "\u001b[47m"   // white background for light squares
  private val DarkBg  = "\u001b[100m"  // dark gray background for dark squares
  private val Fg      = "\u001b[30m"   // black foreground (visible on both)

  def render(board: Board, currentTurn: Color): String =
    val turnLine = if currentTurn == Color.White then "White's turn" else "Black's turn"
    val rows = (7 to 0 by -1).map { row =>
      val cells = (0 to 7).map { col =>
        val bg    = if (col + row) % 2 == 1 then LightBg else DarkBg
        val piece = board.pieceAt(Square(col, row)).map(symbol).getOrElse(' ')
        s"$bg$Fg $piece $Reset"
      }
      s"${row + 1} ${cells.mkString}"
    }
    val labels = "abcdefgh".map(c => s" $c ").mkString
    (turnLine +: rows :+ s"   $labels").mkString("\n")

  private def symbol(piece: Piece): Char = piece match
    case Piece(Color.White, PieceKind.King)   => 'K'
    case Piece(Color.White, PieceKind.Queen)  => 'Q'
    case Piece(Color.White, PieceKind.Rook)   => 'R'
    case Piece(Color.White, PieceKind.Bishop) => 'B'
    case Piece(Color.White, PieceKind.Knight) => 'N'
    case Piece(Color.White, PieceKind.Pawn)   => 'P'
    case Piece(Color.Black, PieceKind.King)   => 'k'
    case Piece(Color.Black, PieceKind.Queen)  => 'q'
    case Piece(Color.Black, PieceKind.Rook)   => 'r'
    case Piece(Color.Black, PieceKind.Bishop) => 'b'
    case Piece(Color.Black, PieceKind.Knight) => 'n'
    case Piece(Color.Black, PieceKind.Pawn)   => 'p'
