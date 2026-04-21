package de.eljachess.botservice

import de.eljachess.chess.model.{Board, Color, Fen, PieceKind, Square}
import scala.util.Random

/**
 * Stateless greedy-random bot engine.
 *
 * Algorithm:
 *   1. Generate all legal moves for the given color.
 *   2. Score each move by capture value + centralisation bonus.
 *   3. Keep the top-N candidates based on ELO (higher ELO = fewer candidates = stronger play).
 *   4. Pick one candidate at random.
 */
object BotEngine:
  private val random = Random()

  /**
   * Compute the best move for the given FEN position, color and ELO.
   *
   * @return Some((from, to)) in algebraic notation, or None if there are no legal moves.
   */
  def bestMove(fen: String, color: Color, elo: Int): Option[(String, String)] =
    Fen.decode(fen) match
      case Left(_)     => None
      case Right(ctrl) =>
        val board      = ctrl.board
        val legalMoves = board.legalMoves(color)
        if legalMoves.isEmpty then return None

        val scored = legalMoves.map { case (from, to) =>
          val captureScore = board.pieceAt(to).map(p => pieceValue(p.kind)).getOrElse(0)
          val posBonus     = positionBonus(board, from, to, color)
          (from, to, captureScore + posBonus)
        }

        val sorted     = scored.sortBy(-_._3)
        val topN       = eloToTopN(elo)
        val candidates = sorted.take(math.min(topN, sorted.length))
        val (from, to, _) = candidates(random.nextInt(candidates.length))
        Some((from.toAlgebraic, to.toAlgebraic))

  // ── Private helpers ──────────────────────────────────────────────────────

  private def pieceValue(kind: PieceKind): Int = kind match
    case PieceKind.Pawn   => 1
    case PieceKind.Knight => 3
    case PieceKind.Bishop => 3
    case PieceKind.Rook   => 5
    case PieceKind.Queen  => 9
    case PieceKind.King   => 0

  private def positionBonus(board: Board, from: Square, to: Square, color: Color): Int =
    var score = 0
    val toDist   = math.abs(to.col   - 3.5) + math.abs(to.row   - 3.5)
    val fromDist = math.abs(from.col - 3.5) + math.abs(from.row - 3.5)
    if toDist < fromDist then score += 1
    if board.pieceAt(from).exists(_.kind == PieceKind.Pawn) then
      val dir = if color == Color.White then 1 else -1
      if (to.row - from.row) * dir > 0 then score += 1
    score

  /** ELO → number of top candidates to pick from (lower = stronger). */
  private def eloToTopN(elo: Int): Int =
    if elo >= 1800 then 1
    else if elo >= 1400 then 3
    else 5
