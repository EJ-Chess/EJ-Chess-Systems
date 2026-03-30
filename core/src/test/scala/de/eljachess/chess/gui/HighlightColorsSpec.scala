package de.eljachess.chess.gui

import de.eljachess.chess.model.{Board, Color as ChessColor, Piece, PieceKind, Square}
import javafx.scene.paint.Color
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HighlightColorsSpec extends AnyFlatSpec with Matchers:

  // A minimal board with one white pawn at e4 and one black pawn at e5.
  private val whitePawn = Piece(ChessColor.White, PieceKind.Pawn)
  private val blackPawn = Piece(ChessColor.Black, PieceKind.Pawn)
  private val e4 = Square(4, 3)
  private val e5 = Square(4, 4)
  private val d5 = Square(3, 4)
  private val board = Board(Map(e4 -> whitePawn, e5 -> blackPawn))

  "HighlightColors.squareColor" should "return yellow for the selected square" in {
    val color = HighlightColors.squareColor(e4, Some(e4), Set.empty, board, isLight = true)
    color shouldBe Color.web("#F6F669")
  }

  it should "return light blue for an empty legal destination" in {
    // d5 is empty and in legalDests
    val color = HighlightColors.squareColor(d5, Some(e4), Set(d5), board, isLight = false)
    color shouldBe Color.web("#ADD8E6")
  }

  it should "return green for a legal destination with an enemy piece" in {
    // e5 has a black pawn — a capturable enemy
    val color = HighlightColors.squareColor(e5, Some(e4), Set(e5), board, isLight = true)
    color shouldBe Color.web("#90EE90")
  }

  it should "return the normal light square colour for a non-target light square" in {
    val color = HighlightColors.squareColor(d5, Some(e4), Set.empty, board, isLight = true)
    color shouldBe Color.web("#F0D9B5")
  }

  it should "return the normal dark square colour for a non-target dark square" in {
    val color = HighlightColors.squareColor(d5, Some(e4), Set.empty, board, isLight = false)
    color shouldBe Color.web("#B58863")
  }

  it should "return the normal light square colour when no piece is selected" in {
    // selected = None → no highlights at all
    val color = HighlightColors.squareColor(e4, None, Set.empty, board, isLight = true)
    color shouldBe Color.web("#F0D9B5")
  }
