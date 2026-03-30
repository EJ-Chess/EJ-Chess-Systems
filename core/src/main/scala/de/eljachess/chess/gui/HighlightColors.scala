package de.eljachess.chess.gui

import de.eljachess.chess.model.{Board, Square}
import javafx.scene.paint.Color

object HighlightColors:

  /** Returns the background colour for a single board square.
   *
   *  Priority (highest -> lowest):
   *  1. Selected square          -> yellow     #F6F669
   *  2. Legal dest + enemy piece -> green      #90EE90
   *  3. Legal dest + empty       -> light blue #ADD8E6
   *  4. Normal light square      -> #F0D9B5
   *  5. Normal dark square       -> #B58863
   *
   *  Note: legalDests is produced by Board.legalMoves which already
   *  excludes friendly-piece destinations, so any piece on a legal
   *  destination is necessarily an enemy.
   */
  def squareColor(
    sq: Square,
    selected: Option[Square],
    legalDests: Set[Square],
    board: Board,
    isLight: Boolean
  ): Color =
    if selected.contains(sq) then Color.web("#F6F669")
    else if legalDests.contains(sq) && board.pieceAt(sq).isDefined
      then Color.web("#90EE90")
    else if legalDests.contains(sq)
      then Color.web("#ADD8E6")
    else if isLight then Color.web("#F0D9B5")
    else Color.web("#B58863")
