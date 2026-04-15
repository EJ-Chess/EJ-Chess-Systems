package de.eljachess.bot

import de.eljachess.chess.model.{Board, Color, Square}

trait Bot:
  /** Returns next move as (from, to) for the given board and bot color.
    * Returns None if no legal moves available.
    */
  def nextMove(board: Board, color: Color): Option[(Square, Square)]

  def elo: Int
