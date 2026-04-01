package de.eljachess.chess.gui

import de.eljachess.chess.controller.{GameController, GameManager, Observer}
import de.eljachess.chess.model.{Color as ChessColor, Piece, PieceKind, Square}
import javafx.application.Platform
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.{Button, ChoiceDialog, Label, TextInputDialog}
import javafx.scene.input.{Clipboard, ClipboardContent}
import scala.jdk.OptionConverters.*
import javafx.scene.layout.{BorderPane, GridPane, HBox, StackPane}
import javafx.scene.shape.Rectangle
import javafx.scene.text.{Font, Text}
import javafx.stage.Stage

// Excluded from scoverage — JavaFX lifecycle cannot be tested headless.
// See docs/unresolved.md for details.
class ChessGUI(manager: GameManager, stage: Stage) extends Observer:

  private val squareSize  = 60
  private val grid        = GridPane()
  private val statusLabel = Label("White's turn")
  private val msgLabel    = Label("")
  private var selected: Option[Square] = None
  private var currentCtrl = manager.state

  def show(): Unit =
    manager.addObserver(this)
    buildScene()
    redrawBoard(currentCtrl)
    stage.show()

  // Called from TUI thread — must dispatch to JavaFX thread via Platform.runLater.
  // Use the `ctrl` parameter as source of truth; do NOT re-read manager.state here.
  def onUpdate(ctrl: GameController, msg: String): Unit =
    Platform.runLater: () =>
      selected = None
      currentCtrl = ctrl
      redrawBoard(ctrl)
      msgLabel.setText(msg)

  private def buildScene(): Unit =
    val root = BorderPane()

    statusLabel.setFont(Font.font(16))
    statusLabel.setPadding(Insets(8))
    root.setTop(statusLabel)

    root.setCenter(grid)

    val undoBtn = Button("Undo")
    val redoBtn = Button("Redo")
    undoBtn.setOnAction(_ => doAction(manager.undo(this)))
    redoBtn.setOnAction(_ => doAction(manager.redo(this)))

    val copyFenBtn   = buildCopyFenButton()
    val loadFenBtn   = buildLoadFenButton()
    val exportPgnBtn = buildExportPgnButton()

    msgLabel.setPadding(Insets(0, 8, 0, 8))
    val toolbar = HBox(8.0, undoBtn, redoBtn, copyFenBtn, loadFenBtn, exportPgnBtn, msgLabel)
    toolbar.setPadding(Insets(8))
    toolbar.setAlignment(Pos.CENTER_LEFT)
    root.setBottom(toolbar)

    stage.setTitle("ElJa Chess")
    stage.setScene(Scene(root, (squareSize * 8).toDouble, (squareSize * 8 + 80).toDouble))
    stage.setResizable(false)

  private def buildCopyFenButton(): Button =
    val btn = Button("Copy FEN")
    btn.setOnAction { _ =>
      val fenStr  = manager.move("fen", this)
      val content = new ClipboardContent()
      content.putString(fenStr)
      Clipboard.getSystemClipboard.setContent(content)
      msgLabel.setText("FEN copied")
    }
    btn

  private def buildLoadFenButton(): Button =
    val btn = Button("Load FEN")
    btn.setOnAction { _ =>
      val dialog = new TextInputDialog()
      dialog.setTitle("FEN laden")
      dialog.setHeaderText("FEN-String eingeben")
      dialog.setContentText("FEN:")
      dialog.showAndWait().toScala match
        case Some(input) if input.nonEmpty =>
          val msg = manager.move(s"load $input", this)
          selected = None; currentCtrl = manager.state; redrawBoard(currentCtrl); msgLabel.setText(msg)
        case _ => ()
    }
    btn

  private def buildExportPgnButton(): Button =
    val btn = Button("Export PGN")
    btn.setOnAction { _ =>
      val dialog = new TextInputDialog()
      dialog.setTitle("Spieler eingeben")
      dialog.setHeaderText("Spielernamen für PGN")
      dialog.setContentText("Weiß, Schwarz (kommagetrennt):")
      dialog.showAndWait().toScala match
        case Some(input) if input.nonEmpty =>
          val names = input.split(",").map(_.trim)
          if names.length == 2 then
            val pgnStr  = manager.pgn(names(0), names(1))
            val content = new ClipboardContent()
            content.putString(pgnStr)
            Clipboard.getSystemClipboard.setContent(content)
            msgLabel.setText("PGN copied")
          else
            msgLabel.setText("Format: White, Black")
        case _ => ()
    }
    btn

  // Called after GUI-originated undo/redo (caller = this, so onUpdate was skipped).
  private def doAction(msg: String): Unit =
    selected = None
    currentCtrl = manager.state
    redrawBoard(currentCtrl)
    msgLabel.setText(msg)

  private def redrawBoard(ctrl: GameController): Unit =
    grid.getChildren.clear()
    statusLabel.setText(if ctrl.currentTurn == ChessColor.White then "White's turn" else "Black's turn")
    val legalDests: Set[Square] =
      selected.map { from =>
        ctrl.board.legalMoves(ctrl.currentTurn)
          .filter(_._1 == from)
          .map(_._2)
          .toSet
      }.getOrElse(Set.empty)
    for row <- 7 to 0 by -1 do
      for col <- 0 to 7 do
        val sq      = Square(col, row)
        val isLight = (col + row) % 2 == 1
        val bg      = HighlightColors.squareColor(sq, selected, legalDests, ctrl.board, isLight)
        val rect    = Rectangle(squareSize.toDouble, squareSize.toDouble, bg)
        val label   = Text(ctrl.board.pieceAt(sq).map(pieceSymbol).getOrElse(""))
        label.setFont(Font.font(36))
        val cell = StackPane(rect, label)
        cell.setOnMouseClicked(_ => handleClick(sq))
        grid.add(cell, col, 7 - row)

  private def handleClick(sq: Square): Unit =
    selected match
      case None =>
        currentCtrl.board.pieceAt(sq) match
          case Some(p) if p.color == currentCtrl.currentTurn =>
            selected = Some(sq)
            redrawBoard(currentCtrl)
          case _ => ()
      case Some(from) if from == sq =>
        selected = None
        redrawBoard(currentCtrl)
      case Some(from) =>
        val isPromotion =
          currentCtrl.board.pieceAt(from).exists(_.kind == PieceKind.Pawn) &&
          ((currentCtrl.currentTurn == ChessColor.White && sq.row == 7) ||
           (currentCtrl.currentTurn == ChessColor.Black && sq.row == 0))
        if isPromotion then
          val dialog = new ChoiceDialog[String]("Q", java.util.List.of("Q", "R", "B", "N"))
          dialog.setTitle("Bauernumwandlung")
          dialog.setHeaderText("Wähle die Umwandlungsfigur")
          dialog.setContentText("Figur (Q=Dame R=Turm B=Läufer N=Springer):")
          val result = dialog.showAndWait()
          if result.isPresent then
            val letter = result.get()
            val msg = manager.move(s"${from.toAlgebraic} ${sq.toAlgebraic} $letter", this)
            selected = None; currentCtrl = manager.state; redrawBoard(currentCtrl); msgLabel.setText(msg)
          else
            selected = None; redrawBoard(currentCtrl)
        else
          val move = s"${from.toAlgebraic} ${sq.toAlgebraic}"
          val msg  = manager.move(move, this)
          selected    = None
          currentCtrl = manager.state
          redrawBoard(currentCtrl)
          msgLabel.setText(msg)

  private def pieceSymbol(piece: Piece): String = piece match
    case Piece(ChessColor.White, PieceKind.King)   => "♔"
    case Piece(ChessColor.White, PieceKind.Queen)  => "♕"
    case Piece(ChessColor.White, PieceKind.Rook)   => "♖"
    case Piece(ChessColor.White, PieceKind.Bishop) => "♗"
    case Piece(ChessColor.White, PieceKind.Knight) => "♘"
    case Piece(ChessColor.White, PieceKind.Pawn)   => "♙"
    case Piece(ChessColor.Black, PieceKind.King)   => "♚"
    case Piece(ChessColor.Black, PieceKind.Queen)  => "♛"
    case Piece(ChessColor.Black, PieceKind.Rook)   => "♜"
    case Piece(ChessColor.Black, PieceKind.Bishop) => "♝"
    case Piece(ChessColor.Black, PieceKind.Knight) => "♞"
    case Piece(ChessColor.Black, PieceKind.Pawn)   => "♟"
