package de.eljachess.chess.gui

import de.eljachess.chess.controller.{GameController, GameManager, Observer, SanDecoder}
import de.eljachess.chess.model.{Color as ChessColor, Pgn, Piece, PieceKind, Square}
import javafx.application.Platform
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.{Button, Label}
import javafx.scene.layout.{BorderPane, GridPane, HBox, StackPane}
import javafx.scene.shape.Rectangle
import javafx.scene.text.{Font, Text}
import javafx.stage.{FileChooser, Stage}

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

    val undoBtn      = Button("Undo")
    val redoBtn      = Button("Redo")
    val importPgnBtn = buildImportPgnButton(manager)
    undoBtn.setOnAction(_ => doAction(manager.undo(this)))
    redoBtn.setOnAction(_ => doAction(manager.redo(this)))
    msgLabel.setPadding(Insets(0, 8, 0, 8))
    val toolbar = HBox(8.0, undoBtn, redoBtn, importPgnBtn, msgLabel)
    toolbar.setPadding(Insets(8))
    toolbar.setAlignment(Pos.CENTER_LEFT)
    root.setBottom(toolbar)

    stage.setTitle("ElJa Chess")
    stage.setScene(Scene(root, (squareSize * 8).toDouble, (squareSize * 8 + 80).toDouble))
    stage.setResizable(false)

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
        val move = s"${from.toAlgebraic} ${sq.toAlgebraic}"
        val msg  = manager.move(move, this)   // TUI is notified; GUI is not (caller = this)
        selected     = None
        // manager.state is read immediately after move() returns on the JavaFX thread;
        // a concurrent TUI move between the two calls is possible but rare in practice.
        // Proper fix: have GameManager.move return (GameController, String).
        currentCtrl  = manager.state
        redrawBoard(currentCtrl)
        msgLabel.setText(msg)

  // $COVERAGE-OFF$
  private def buildImportPgnButton(manager: GameManager): Button =
    val button = new Button("Import PGN")
    button.setOnAction { _ =>
      val chooser = new FileChooser()
      chooser.setTitle("Import PGN File")
      chooser.getExtensionFilters.add(
        new FileChooser.ExtensionFilter("PGN files (*.pgn)", "*.pgn")
      )
      val file = chooser.showOpenDialog(stage)
      if file != null then
        try
          val content = scala.io.Source.fromFile(file, "UTF-8").mkString
          Pgn.decode(content) match
            case Left(err)              => msgLabel.setText(s"PGN parse error: $err")
            case Right((headers, moves)) => replayPgn(moves, manager, headers)
        catch
          case _: java.io.IOException =>
            msgLabel.setText(s"Cannot read file: ${file.getName}")
          case _: java.nio.charset.MalformedInputException =>
            msgLabel.setText("File encoding error: expected UTF-8")
    }
    button

  private def replayPgn(moves: List[String], manager: GameManager, headers: Map[String, String]): Unit =
    var halfmove = 1
    val iter     = moves.iterator
    var stopped  = false
    while iter.hasNext && !stopped do
      val san  = iter.next()
      val ctrl = manager.state
      SanDecoder.expand(ctrl.board, ctrl.currentTurn, san) match
        case Left(err) =>
          msgLabel.setText(s"Halfmove $halfmove: $err")
          stopped = true
        case Right((from, to, promo)) =>
          val algebraic = s"${from.toAlgebraic} ${to.toAlgebraic}" +
            promo.map(k => s" ${k.toString.charAt(0)}").getOrElse("")
          val msg = manager.move(algebraic, this)
          if msg.startsWith("Invalid") || msg.startsWith("It's") || msg.startsWith("No piece") then
            msgLabel.setText(s"Halfmove $halfmove: $msg")
            stopped = true
          else
            halfmove += 1
    if !stopped then
      val w = headers.getOrElse("White", "White")
      val b = headers.getOrElse("Black", "Black")
      msgLabel.setText(s"PGN imported: $w vs $b")
  // $COVERAGE-ON$

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
