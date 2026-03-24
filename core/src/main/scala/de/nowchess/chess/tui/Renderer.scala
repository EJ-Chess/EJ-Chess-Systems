// core/src/main/scala/de/nowchess/chess/tui/Renderer.scala
package de.nowchess.chess.tui

import de.nowchess.chess.model.{Board, Color, Piece, PieceKind, Square}

object Renderer:

  def render(board: Board): String =
    val rows = (7 to 0 by -1).map { row =>
      val cells = (0 to 7).map { col =>
        board.pieceAt(Square(col, row)).map(symbol).getOrElse('·')
      }
      s"${row + 1} ${cells.mkString(" ")}"
    }
    (rows :+ "  a b c d e f g h").mkString("\n")

  private def symbol(piece: Piece): Char = piece match
    case Piece(Color.White, PieceKind.King)   => '♔'
    case Piece(Color.White, PieceKind.Queen)  => '♕'
    case Piece(Color.White, PieceKind.Rook)   => '♖'
    case Piece(Color.White, PieceKind.Bishop) => '♗'
    case Piece(Color.White, PieceKind.Knight) => '♘'
    case Piece(Color.White, PieceKind.Pawn)   => '♙'
    case Piece(Color.Black, PieceKind.King)   => '♚'
    case Piece(Color.Black, PieceKind.Queen)  => '♛'
    case Piece(Color.Black, PieceKind.Rook)   => '♜'
    case Piece(Color.Black, PieceKind.Bishop) => '♝'
    case Piece(Color.Black, PieceKind.Knight) => '♞'
    case Piece(Color.Black, PieceKind.Pawn)   => '♟'
