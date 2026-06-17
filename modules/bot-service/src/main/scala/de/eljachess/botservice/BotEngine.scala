package de.eljachess.botservice

import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Square}

/**
 * Chess bot engine: Minimax with Alpha-Beta pruning.
 *
 * Search:
 *   - Builds a game tree to `depth` half-moves (plies).
 *   - White = maximising player, Black = minimising player.
 *   - Alpha-Beta pruning eliminates branches that cannot change the result,
 *     reducing worst-case nodes from O(b^d) to O(b^(d/2)).
 *
 * Evaluation (leaf nodes):
 *   - Material score in centipawns (P=100, N=320, B=330, R=500, Q=900, K=20000).
 *   - Piece-square table bonus: encodes opening principles
 *     (knights to the centre, rooks on open files, king safety, …).
 *   - Score is always from White's perspective (positive = White better).
 *
 * ELO → search depth:
 *   < 1000  → depth 1 (single ply, fast)
 *   1000–1399 → depth 2
 *   1400–1799 → depth 3
 *   ≥ 1800  → depth 4
 */
object BotEngine:

  /** Score assigned to a position where the side to move is checkmated. */
  private val MateScore = 100_000

  // ── Public API ──────────────────────────────────────────────────────────────

  /**
   * Compute the best move for the given FEN position, colour and ELO.
   *
   * @return Some((from, to)) in algebraic notation, or None if no legal moves exist.
   */
  def bestMove(fen: String, color: Color, elo: Int): Option[(String, String)] =
    Fen.decode(fen) match
      case Left(_)     => None
      case Right(ctrl) =>
        val board = ctrl.board
        val moves = board.legalMoves(color)
        if moves.isEmpty then return None

        val depth        = eloToDepth(elo)
        val isMaximizing = color == Color.White
        var bestScore    = if isMaximizing then Int.MinValue + 1 else Int.MaxValue - 1
        var best: Option[(Square, Square)] = None

        for (from, to) <- moves do
          board.move(from, to, Some(PieceKind.Queen)).foreach { newBoard =>
            val score = minimax(newBoard, depth - 1, Int.MinValue + 1, Int.MaxValue - 1, !isMaximizing)
            if best.isEmpty
              || (isMaximizing  && score > bestScore)
              || (!isMaximizing && score < bestScore)
            then
              bestScore = score
              best      = Some((from, to))
          }

        best.map((from, to) => (from.toAlgebraic, to.toAlgebraic))

  // ── Minimax + Alpha-Beta ────────────────────────────────────────────────────

  private def minimax(board: Board, depth: Int, alpha: Int, beta: Int, maximizing: Boolean): Int =
    val color = if maximizing then Color.White else Color.Black
    val moves = board.legalMoves(color)

    if moves.isEmpty then
      // No legal moves → checkmate or stalemate
      // MateScore + depth: faster mates score higher (depth is higher near the root)
      if board.isInCheck(color) then
        if maximizing then -(MateScore + depth) else (MateScore + depth)
      else 0  // stalemate: equal by rule
    else if depth == 0 then
      evaluate(board)
    else
      // Move ordering: try captures first → more pruning, faster search
      val ordered = moves.sortBy { case (_, to) =>
        board.pieceAt(to).map(p => -pieceValue(p.kind)).getOrElse(0)
      }
      var a    = alpha
      var b    = beta
      var best = if maximizing then Int.MinValue + 1 else Int.MaxValue - 1
      var cut  = false
      val iter = ordered.iterator

      while iter.hasNext && !cut do
        val (from, to) = iter.next()
        board.move(from, to, Some(PieceKind.Queen)).foreach { newBoard =>
          val score = minimax(newBoard, depth - 1, a, b, !maximizing)
          if maximizing then
            if score > best then best = score
            if score > a    then a    = score
          else
            if score < best then best = score
            if score < b    then b    = score
          if b <= a then cut = true  // prune
        }

      best

  // ── Static evaluation ───────────────────────────────────────────────────────

  /**
   * Returns a score from White's perspective (centipawns):
   *   positive → White is better
   *   negative → Black is better
   */
  private def evaluate(board: Board): Int =
    board.grid.foldLeft(0) { case (acc, (sq, piece)) =>
      val sign  = if piece.color == Color.White then 1 else -1
      val value = pieceValue(piece.kind) + pieceSquareBonus(piece.kind, piece.color, sq)
      acc + sign * value
    }

  // ── Piece values (centipawns) ───────────────────────────────────────────────

  private def pieceValue(kind: PieceKind): Int = kind match
    case PieceKind.Pawn   => 100
    case PieceKind.Knight => 320
    case PieceKind.Bishop => 330
    case PieceKind.Rook   => 500
    case PieceKind.Queen  => 900
    case PieceKind.King   => 20_000

  // ── Piece-square tables ─────────────────────────────────────────────────────

  /**
   * Positional bonus (centipawns) for placing `kind` on `sq`.
   *
   * Tables are stored from White's point of view (index 0 = rank 8, index 63 = rank 1).
   * For White pieces:  tableIndex = (7 − row) × 8 + col
   * For Black pieces:  tableIndex = row × 8 + col   (mirror vertically)
   */
  private def pieceSquareBonus(kind: PieceKind, color: Color, sq: Square): Int =
    val idx = color match
      case Color.White => (7 - sq.row) * 8 + sq.col
      case Color.Black => sq.row * 8 + sq.col
    kind match
      case PieceKind.Pawn   => PAWN_TABLE(idx)
      case PieceKind.Knight => KNIGHT_TABLE(idx)
      case PieceKind.Bishop => BISHOP_TABLE(idx)
      case PieceKind.Rook   => ROOK_TABLE(idx)
      case PieceKind.Queen  => QUEEN_TABLE(idx)
      case PieceKind.King   => KING_TABLE(idx)

  // format: off
  /** Encourages pawns to advance and control the centre. */
  private val PAWN_TABLE = Array(
     0,  0,  0,  0,  0,  0,  0,  0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
     5,  5, 10, 25, 25, 10,  5,  5,
     0,  0,  0, 20, 20,  0,  0,  0,
     5, -5,-10,  0,  0,-10, -5,  5,
     5, 10, 10,-20,-20, 10, 10,  5,
     0,  0,  0,  0,  0,  0,  0,  0
  )

  /** Knights are strong in the centre and weak on the rim. */
  private val KNIGHT_TABLE = Array(
    -50,-40,-30,-30,-30,-30,-40,-50,
    -40,-20,  0,  0,  0,  0,-20,-40,
    -30,  0, 10, 15, 15, 10,  0,-30,
    -30,  5, 15, 20, 20, 15,  5,-30,
    -30,  0, 15, 20, 20, 15,  0,-30,
    -30,  5, 10, 15, 15, 10,  5,-30,
    -40,-20,  0,  5,  5,  0,-20,-40,
    -50,-40,-30,-30,-30,-30,-40,-50
  )

  /** Bishops prefer long diagonals. */
  private val BISHOP_TABLE = Array(
    -20,-10,-10,-10,-10,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5, 10, 10,  5,  0,-10,
    -10,  5,  5, 10, 10,  5,  5,-10,
    -10,  0, 10, 10, 10, 10,  0,-10,
    -10, 10, 10, 10, 10, 10, 10,-10,
    -10,  5,  0,  0,  0,  0,  5,-10,
    -20,-10,-10,-10,-10,-10,-10,-20
  )

  /** Rooks belong on open files and the 7th rank. */
  private val ROOK_TABLE = Array(
     0,  0,  0,  0,  0,  0,  0,  0,
     5, 10, 10, 10, 10, 10, 10,  5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     0,  0,  0,  5,  5,  0,  0,  0
  )

  /** Queen combines rook and bishop bonuses. */
  private val QUEEN_TABLE = Array(
    -20,-10,-10, -5, -5,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5,  5,  5,  5,  0,-10,
     -5,  0,  5,  5,  5,  5,  0, -5,
      0,  0,  5,  5,  5,  5,  0, -5,
    -10,  5,  5,  5,  5,  5,  0,-10,
    -10,  0,  5,  0,  0,  0,  0,-10,
    -20,-10,-10, -5, -5,-10,-10,-20
  )

  /** King prefers castled safety in the middlegame; avoids the centre. */
  private val KING_TABLE = Array(
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -20,-30,-30,-40,-40,-30,-30,-20,
    -10,-20,-20,-20,-20,-20,-20,-10,
     20, 20,  0,  0,  0,  0, 20, 20,
     20, 30, 10,  0,  0, 10, 30, 20
  )
  // format: on

  // ── ELO → depth ────────────────────────────────────────────────────────────

  private def eloToDepth(elo: Int): Int =
    if      elo >= 1800 then 4
    else if elo >= 1400 then 3
    else if elo >= 1000 then 2
    else                     1
