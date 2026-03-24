// core/src/main/scala/de/eljachess/chess/model/Board.scala
package de.eljachess.chess.model

case class Board(grid: Map[Square, Piece]):

  def move(from: Square, to: Square): Option[Board] =
    grid.get(from).flatMap { piece =>
      val valid = piece.kind match
        case PieceKind.Pawn   => isValidPawnMove(from, to, piece.color)
        case PieceKind.Rook   => isValidRookMove(from, to, piece.color)
        case PieceKind.Bishop => isValidBishopMove(from, to, piece.color)
        case PieceKind.Knight => isValidKnightMove(from, to, piece.color)
        case PieceKind.Queen  => isValidQueenMove(from, to, piece.color)
        case PieceKind.King   => isValidKingMove(from, to, piece.color)
      if valid then Some(Board(grid - from + (to -> piece)))
      else None
    }

  def pieceAt(square: Square): Option[Piece] = grid.get(square)

  // Returns true if every square strictly between from and to is empty.
  // Works for straight (rook-style) and diagonal (bishop-style) paths.
  private def isPathClear(from: Square, to: Square): Boolean =
    val rowStep = (to.row - from.row).sign
    val colStep = (to.col - from.col).sign
    val path = Iterator
      .iterate(Square(from.col + colStep, from.row + rowStep))(s => Square(s.col + colStep, s.row + rowStep))
      .takeWhile(_ != to)
    path.forall(s => pieceAt(s).isEmpty)

  private def isValidRookMove(from: Square, to: Square, color: Color): Boolean =
    val dr = to.row - from.row
    val dc = to.col - from.col
    if dr != 0 && dc != 0 then return false
    if dr == 0 && dc == 0 then return false
    if pieceAt(to).exists(_.color == color) then return false
    isPathClear(from, to)

  private def isValidBishopMove(from: Square, to: Square, color: Color): Boolean =
    val dr = to.row - from.row
    val dc = to.col - from.col
    if math.abs(dr) != math.abs(dc) || dr == 0 then return false
    if pieceAt(to).exists(_.color == color) then return false
    isPathClear(from, to)

  private def isValidKnightMove(from: Square, to: Square, color: Color): Boolean =
    val dr = math.abs(to.row - from.row)
    val dc = math.abs(to.col - from.col)
    if !((dr == 2 && dc == 1) || (dr == 1 && dc == 2)) then return false
    pieceAt(to).forall(_.color != color)

  private def isValidQueenMove(from: Square, to: Square, color: Color): Boolean =
    val dr = to.row - from.row
    val dc = to.col - from.col
    val isDiagonal = math.abs(dr) == math.abs(dc) && dr != 0
    val isStraight  = (dr == 0) != (dc == 0)   // exactly one of them is non-zero
    if !isDiagonal && !isStraight then return false
    if pieceAt(to).exists(_.color == color) then return false
    isPathClear(from, to)

  private def isValidKingMove(from: Square, to: Square, color: Color): Boolean =
    val dr = math.abs(to.row - from.row)
    val dc = math.abs(to.col - from.col)
    if dr > 1 || dc > 1 || (dr == 0 && dc == 0) then return false
    pieceAt(to).forall(_.color != color)

  private def isValidPawnMove(from: Square, to: Square, color: Color): Boolean =
    val direction = if color == Color.White then 1 else -1
    val startRow  = if color == Color.White then 1 else 6
    val dr = to.row - from.row
    val dc = to.col - from.col

    if dc == 0 && dr == direction then
      pieceAt(to).isEmpty
    else if dc == 0 && dr == 2 * direction && from.row == startRow then
      val intermediate = Square(from.col, from.row + direction)
      pieceAt(intermediate).isEmpty && pieceAt(to).isEmpty
    else if math.abs(dc) == 1 && dr == direction then
      pieceAt(to).exists(_.color != color)
    else
      false

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
