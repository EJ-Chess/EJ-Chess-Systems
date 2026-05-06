// core/src/main/scala/de/eljachess/chess/model/Board.scala
package de.eljachess.chess.model

case class Board(
  grid:            Map[Square, Piece],
  castlingRights:  CastlingRights = CastlingRights(),
  enPassantTarget: Option[Square] = None
):

  def move(from: Square, to: Square, promotion: Option[PieceKind] = None): Option[Board] =
    grid.get(from).flatMap { piece =>
      // Step 1: Castling
      if piece.kind == PieceKind.King && math.abs(to.col - from.col) == 2 then
        castlingMove(from, to, piece.color)
      // Step 2: En passant capture (diagonal pawn move onto empty enPassantTarget)
      else if piece.kind == PieceKind.Pawn &&
              enPassantTarget.contains(to) &&
              math.abs(to.col - from.col) == 1 &&
              pieceAt(to).isEmpty then
        val direction = if piece.color == Color.White then 1 else -1
        if from.row + direction == to.row then
          enPassantCapture(from, to, piece.color)
        else
          None
      // Step 3: Promotion (pawn reaches back rank)
      else if piece.kind == PieceKind.Pawn &&
              ((piece.color == Color.White && to.row == 7) ||
               (piece.color == Color.Black && to.row == 0)) then
        promotionMove(from, to, piece.color, promotion)
      // Step 4: Normal move
      else
        val valid = piece.kind match
          case PieceKind.Pawn   => isValidPawnMove(from, to, piece.color)
          case PieceKind.Rook   => isValidRookMove(from, to, piece.color)
          case PieceKind.Bishop => isValidBishopMove(from, to, piece.color)
          case PieceKind.Knight => isValidKnightMove(from, to, piece.color)
          case PieceKind.Queen  => isValidQueenMove(from, to, piece.color)
          case PieceKind.King   => isValidKingMove(from, to, piece.color)
        if valid then
          val newGrid     = grid - from + (to -> piece)
          val newEpTarget =
            if piece.kind == PieceKind.Pawn && math.abs(to.row - from.row) == 2
            then Some(Square(from.col, (from.row + to.row) / 2))
            else None
          Some(Board(newGrid, updatedCastlingRights(from, to, piece), newEpTarget))
        else None
    }

  def pieceAt(square: Square): Option[Piece] = grid.get(square)

  /** True if the king of `color` is currently under attack.
   *  Uses piece validators directly to avoid recursion through castlingMove. */
  def isInCheck(color: Color): Boolean =
    val kingPos = grid.collectFirst { case (sq, Piece(c, PieceKind.King)) if c == color => sq }
    kingPos.exists { ks =>
      grid.exists {
        case (from, Piece(c, kind)) if c != color =>
          kind match
            case PieceKind.Pawn   => isValidPawnMove(from, ks, c)
            case PieceKind.Rook   => isValidRookMove(from, ks, c)
            case PieceKind.Bishop => isValidBishopMove(from, ks, c)
            case PieceKind.Knight => isValidKnightMove(from, ks, c)
            case PieceKind.Queen  => isValidQueenMove(from, ks, c)
            case PieceKind.King   => isValidKingMove(from, ks, c)
        case _ => false
      }
    }

  /** All moves for `color` that do not leave the own king in check. */
  def legalMoves(color: Color): List[(Square, Square)] =
    for
      from <- grid.keys.toList if grid(from).color == color
      to   <- candidateTargets(from, grid(from))
      isBackRankPawn = grid(from).kind == PieceKind.Pawn &&
        ((color == Color.White && to.row == 7) || (color == Color.Black && to.row == 0))
      newBoard <- if isBackRankPawn then move(from, to, Some(PieceKind.Queen)) else move(from, to)
      if !newBoard.isInCheck(color)
    yield (from, to)

  /** Geometrically reachable squares for a piece — far fewer than all 64.
   *  Legality (check, path clear for sliding pieces) is still enforced by move(). */
  private def candidateTargets(from: Square, piece: Piece): List[Square] =
    piece.kind match
      case PieceKind.Knight => knightCandidates(from)
      case PieceKind.King   => kingCandidates(from)
      case PieceKind.Pawn   => pawnCandidates(from, piece.color)
      case PieceKind.Rook   => slidingCandidates(from, List((1,0),(-1,0),(0,1),(0,-1)))
      case PieceKind.Bishop => slidingCandidates(from, List((1,1),(1,-1),(-1,1),(-1,-1)))
      case PieceKind.Queen  =>
        slidingCandidates(from, List((1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1)))

  private def knightCandidates(from: Square): List[Square] =
    List((-2,-1),(-2,1),(-1,-2),(-1,2),(1,-2),(1,2),(2,-1),(2,1)).collect {
      case (dc, dr) if from.col+dc >= 0 && from.col+dc < 8 &&
                       from.row+dr >= 0 && from.row+dr < 8 =>
        Square(from.col + dc, from.row + dr)
    }

  private def kingCandidates(from: Square): List[Square] =
    val adjacent =
      for dc <- -1 to 1; dr <- -1 to 1
          if !(dc == 0 && dr == 0)
          c = from.col + dc; r = from.row + dr
          if c >= 0 && c < 8 && r >= 0 && r < 8
      yield Square(c, r)
    // castling squares — move() validates rights, path, and check
    val castling = List(from.col - 2, from.col + 2)
      .filter(c => c >= 0 && c < 8)
      .map(c => Square(c, from.row))
    adjacent.toList ++ castling

  private def pawnCandidates(from: Square, color: Color): List[Square] =
    val dir      = if color == Color.White then 1 else -1
    val startRow = if color == Color.White then 1 else 6
    val r1 = from.row + dir
    if r1 < 0 || r1 > 7 then return Nil
    val forward       = List(Square(from.col, r1))
    val doubleForward = if from.row == startRow then List(Square(from.col, from.row + 2*dir)) else Nil
    // diagonal squares: normal captures + en passant (move() distinguishes)
    val captures = List(-1, 1).collect {
      case dc if from.col + dc >= 0 && from.col + dc < 8 => Square(from.col + dc, r1)
    }
    forward ++ doubleForward ++ captures

  // Walk each ray and stop after the first occupied square (inclusive).
  private def slidingCandidates(from: Square, dirs: List[(Int, Int)]): List[Square] =
    dirs.flatMap { case (dc, dr) =>
      val ray = scala.collection.mutable.ListBuffer.empty[Square]
      var c = from.col + dc; var r = from.row + dr
      var blocked = false
      while c >= 0 && c < 8 && r >= 0 && r < 8 && !blocked do
        val sq = Square(c, r)
        ray += sq
        if grid.contains(sq) then blocked = true
        c += dc; r += dr
      ray.toList
    }

  private def updatedCastlingRights(from: Square, to: Square, piece: Piece): CastlingRights =
    var r = castlingRights
    // King moves: clear both rights for that colour
    if piece.kind == PieceKind.King then
      r = piece.color match
        case Color.White => r.copy(whiteKingside = false, whiteQueenside = false)
        case Color.Black => r.copy(blackKingside = false, blackQueenside = false)
    // Rook moves from home square
    if piece.kind == PieceKind.Rook then
      if      from == Square(0, 0) then r = r.copy(whiteQueenside = false)
      else if from == Square(7, 0) then r = r.copy(whiteKingside  = false)
      else if from == Square(0, 7) then r = r.copy(blackQueenside = false)
      else if from == Square(7, 7) then r = r.copy(blackKingside  = false)
    // Rook captured on home square
    if      to == Square(0, 0) then r = r.copy(whiteQueenside = false)
    else if to == Square(7, 0) then r = r.copy(whiteKingside  = false)
    else if to == Square(0, 7) then r = r.copy(blackQueenside = false)
    else if to == Square(7, 7) then r = r.copy(blackKingside  = false)
    r

  private def castlingMove(from: Square, to: Square, color: Color): Option[Board] =
    if to.row != from.row then return None   // castling must stay on the same rank
    val kingside = to.col > from.col
    val hasRight = (color, kingside) match
      case (Color.White, true)  => castlingRights.whiteKingside
      case (Color.White, false) => castlingRights.whiteQueenside
      case (Color.Black, true)  => castlingRights.blackKingside
      case (Color.Black, false) => castlingRights.blackQueenside
    if !hasRight then return None

    val rookFromCol = if kingside then 7 else 0
    val rookFrom    = Square(rookFromCol, from.row)
    if pieceAt(rookFrom).isEmpty then return None  // rook must be present

    val between =
      if kingside then List(Square(5, from.row), Square(6, from.row))
      else             List(Square(1, from.row), Square(2, from.row), Square(3, from.row))
    if between.exists(sq => pieceAt(sq).isDefined) then return None

    if isInCheck(color) then return None

    // Check transit square (king must not pass through attacked square)
    val transitCol   = if kingside then 5 else 3
    val transitBoard = Board(
      grid - from + (Square(transitCol, from.row) -> Piece(color, PieceKind.King)),
      castlingRights, enPassantTarget
    )
    if transitBoard.isInCheck(color) then return None

    // Check that king's destination is not attacked
    val destBoard = Board(
      grid - from + (to -> Piece(color, PieceKind.King)),
      castlingRights, enPassantTarget
    )
    if destBoard.isInCheck(color) then return None

    val rookToCol = if kingside then 5 else 3
    val newRights = color match
      case Color.White => castlingRights.copy(whiteKingside = false, whiteQueenside = false)
      case Color.Black => castlingRights.copy(blackKingside = false, blackQueenside = false)
    val newGrid =
      grid - from - rookFrom +
      (to                          -> Piece(color, PieceKind.King)) +
      (Square(rookToCol, from.row) -> Piece(color, PieceKind.Rook))
    Some(Board(newGrid, newRights, None))

  private def enPassantCapture(from: Square, to: Square, color: Color): Option[Board] =
    val capturedPawnSq = Square(to.col, from.row)
    val newGrid = grid - from - capturedPawnSq + (to -> Piece(color, PieceKind.Pawn))
    Some(Board(newGrid, castlingRights, None))

  private def promotionMove(from: Square, to: Square, color: Color, promotion: Option[PieceKind]): Option[Board] =
    promotion match
      case None                                         => None
      case Some(PieceKind.King) | Some(PieceKind.Pawn) => None
      case Some(kind) =>
        val valid = isValidPawnMove(from, to, color)
        if valid then
          val newGrid = grid - from + (to -> Piece(color, kind))
          Some(Board(newGrid, updatedCastlingRights(from, to, Piece(color, PieceKind.Pawn)), None))
        else None

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
