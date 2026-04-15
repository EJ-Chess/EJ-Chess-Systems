package de.eljachess.bot

import de.eljachess.chess.model.{Board, Color, Piece, PieceKind, Square}
import scala.util.Random

case class GreedyRandomBot(eloLevel: EloLevel) extends Bot:
  private val random = Random()

  def elo: Int = eloLevel.elo

  def nextMove(board: Board, color: Color): Option[(Square, Square)] =
    val legalMoves = board.legalMoves(color)
    if legalMoves.isEmpty then return None

    val scoredMoves = legalMoves.map { case (from, to) =>
      val captureScore  = board.pieceAt(to).map(p => pieceValue(p.kind)).getOrElse(0)
      val posBonus      = positionScore(board, from, to, color)
      (from, to, captureScore + posBonus)
    }

    val sorted     = scoredMoves.sortBy(-_._3)
    val topN       = eloToTopN(eloLevel)
    val candidates = sorted.take(math.min(topN, sorted.length))
    val selected   = candidates(random.nextInt(candidates.length))
    Some((selected._1, selected._2))

  private def pieceValue(kind: PieceKind): Int = kind match
    case PieceKind.Pawn   => 1
    case PieceKind.Knight => 3
    case PieceKind.Bishop => 3
    case PieceKind.Rook   => 5
    case PieceKind.Queen  => 9
    case PieceKind.King   => 0

  private def positionScore(board: Board, from: Square, to: Square, color: Color): Int =
    var score = 0
    val toDist   = math.abs(to.col - 3.5)   + math.abs(to.row - 3.5)
    val fromDist = math.abs(from.col - 3.5) + math.abs(from.row - 3.5)
    if toDist < fromDist then score += 1
    if board.pieceAt(from).exists(_.kind == PieceKind.Pawn) then
      val dir = if color == Color.White then 1 else -1
      if (to.row - from.row) * dir > 0 then score += 1
    score

  private def eloToTopN(level: EloLevel): Int =
    if level.elo >= 1800 then 1
    else if level.elo >= 1400 then 3
    else 5
