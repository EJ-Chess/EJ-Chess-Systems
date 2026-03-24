// core/src/main/scala/de/eljachess/chess/model/Board.scala
package de.eljachess.chess.model

case class Board(grid: Map[Square, Piece]):

  def move(from: Square, to: Square): Option[Board] =
    grid.get(from).map(piece => Board(grid - from + (to -> piece)))

  def pieceAt(square: Square): Option[Piece] = grid.get(square)

object Board:
  def initial: Board =
    val backRank = List(
      PieceKind.Rook, PieceKind.Knight, PieceKind.Bishop, PieceKind.Queen,
      PieceKind.King, PieceKind.Bishop, PieceKind.Knight, PieceKind.Rook
    )
    val pieces =
      (0 to 7).map(col => Square(col, 0) -> Piece(Color.White, backRank(col))) ++
      (0 to 7).map(col => Square(col, 1) -> Piece(Color.White, PieceKind.Pawn)) ++
      (0 to 7).map(col => Square(col, 6) -> Piece(Color.Black, PieceKind.Pawn)) ++
      (0 to 7).map(col => Square(col, 7) -> Piece(Color.Black, backRank(col)))
    Board(pieces.toMap)
