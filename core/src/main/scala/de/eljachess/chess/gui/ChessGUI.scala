package de.eljachess.chess.gui

import de.eljachess.chess.controller.{GameController, GameManager, Observer, SanDecoder}
import de.eljachess.chess.model.{Color as ChessColor, Fen, Json, Pgn, Piece, PieceKind, Square}
import javafx.application.Platform
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.{Button, ChoiceDialog, Label, TextInputDialog}
import javafx.scene.input.{Clipboard, ClipboardContent}
import scala.jdk.OptionConverters.*
import javafx.scene.layout.{BorderPane, GridPane, HBox, StackPane, VBox}
import javafx.scene.shape.Rectangle
import javafx.scene.text.{Font, Text}
import javafx.stage.{FileChooser, Stage}

// Excluded from scoverage — JavaFX lifecycle cannot be tested headless.
// See docs/unresolved.md for details.
class ChessGUI(manager: GameManager, stage: Stage) extends Observer:

  private val grid        = GridPane()
  private val statusLabel = Label("White's turn")
  private val msgLabel    = Label("")
  private var selected: Option[Square] = None
  private var currentCtrl = manager.state
  private val overlayPane: VBox =
    val box = VBox(20.0)
    box.setAlignment(Pos.CENTER)
    box.setMaxWidth(Double.MaxValue)
    box.setMaxHeight(Double.MaxValue)
    box.setStyle("-fx-background-color: rgba(0,0,0,0.65);")
    box.setVisible(false)
    box

  private def squareSize: Int =
    val w = grid.getWidth
    val h = grid.getHeight
    if w > 0 && h > 0 then (math.min(w, h) / 8).toInt.max(40)
    else 60

  def show(): Unit =
    manager.addObserver(this)
    buildScene()
    redrawBoard(currentCtrl)
    stage.show()
    grid.widthProperty().addListener((_, _, _) => Platform.runLater(() => redrawBoard(currentCtrl)))
    grid.heightProperty().addListener((_, _, _) => Platform.runLater(() => redrawBoard(currentCtrl)))

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

    root.setCenter(StackPane(grid, overlayPane))

    val undoBtn      = Button("Undo")
    val redoBtn      = Button("Redo")
    undoBtn.setOnAction(_ => doAction(manager.undo(this)))
    redoBtn.setOnAction(_ => doAction(manager.redo(this)))

    val copyFenBtn   = buildCopyFenButton()
    val loadFenBtn   = buildLoadFenButton()
    val importPgnBtn  = buildImportPgnButton(manager)
    val exportPgnBtn  = buildExportPgnButton()
    val importJsonBtn = buildImportJsonButton(manager)
    val exportJsonBtn = buildExportJsonButton()

    val btnGrid = GridPane()
    btnGrid.setHgap(6)
    btnGrid.setVgap(6)
    btnGrid.add(undoBtn,       0, 0); btnGrid.add(redoBtn,       1, 0)
    btnGrid.add(copyFenBtn,    0, 1); btnGrid.add(loadFenBtn,    1, 1)
    btnGrid.add(importPgnBtn,  0, 2); btnGrid.add(exportPgnBtn,  1, 2)
    btnGrid.add(importJsonBtn, 0, 3); btnGrid.add(exportJsonBtn, 1, 3)

    msgLabel.setPadding(Insets(8, 4, 4, 4))
    msgLabel.setWrapText(true)
    msgLabel.setMaxWidth(170)

    val sidebar = VBox(10.0, btnGrid, msgLabel)
    sidebar.setPadding(Insets(12))
    sidebar.setAlignment(Pos.TOP_LEFT)
    root.setRight(sidebar)

    stage.setTitle("ElJa Chess")
    stage.setScene(Scene(root, 660.0, 520.0))
    stage.setResizable(true)
    stage.setMinWidth(8 * 40 + 190)
    stage.setMinHeight(8 * 40 + 40)

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
        val sz      = squareSize
        val rect    = Rectangle(sz.toDouble, sz.toDouble, bg)
        val label   = Text(ctrl.board.pieceAt(sq).map(pieceSymbol).getOrElse(""))
        label.setFont(Font.font((sz * 0.6).max(16)))
        val cell = StackPane(rect, label)
        cell.setOnMouseClicked(_ => handleClick(sq))
        grid.add(cell, col, 7 - row)
    checkGameOver(ctrl)

  private def checkGameOver(ctrl: GameController): Unit =
    val nextTurn = ctrl.currentTurn
    if ctrl.board.legalMoves(nextTurn).isEmpty then
      if ctrl.board.isInCheck(nextTurn) then
        val winner = if nextTurn == ChessColor.White then "Black" else "White"
        showGameOver(s"Schachmatt — $winner gewinnt!")
      else
        showGameOver("Patt — Unentschieden!")
    else
      overlayPane.setVisible(false)

  private def showGameOver(message: String): Unit =
    overlayPane.getChildren.clear()
    val title = Label(message)
    title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: white;")
    val newGameBtn = Button("Neues Spiel")
    newGameBtn.setStyle("-fx-font-size: 14px;")
    newGameBtn.setOnAction { _ =>
      overlayPane.setVisible(false)
      doAction(manager.move("load rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", this))
    }
    overlayPane.getChildren.addAll(title, newGameBtn)
    overlayPane.setVisible(true)

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

  // $COVERAGE-OFF$
  private def buildExportJsonButton(): Button =
    val btn = Button("Export JSON")
    btn.setOnAction { _ =>
      val dialog = new TextInputDialog()
      dialog.setTitle("Spieler eingeben")
      dialog.setHeaderText("Spielernamen für JSON")
      dialog.setContentText("Weiß, Schwarz (kommagetrennt):")
      dialog.showAndWait().toScala match
        case Some(input) if input.nonEmpty =>
          val names = input.split(",").map(_.trim)
          if names.length == 2 then
            try
              val json = Json.encode(manager.state, names(0), names(1))
              val chooser = new FileChooser()
              chooser.setTitle("Export JSON File")
              chooser.getExtensionFilters.add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json")
              )
              val file = chooser.showSaveDialog(stage)
              if file != null then
                java.nio.file.Files.write(
                  java.nio.file.Paths.get(file.getAbsolutePath),
                  json.getBytes("UTF-8")
                )
                msgLabel.setText("JSON exported")
            catch
              case e: java.io.IOException => msgLabel.setText(s"JSON error: ${e.getMessage}")
          else
            msgLabel.setText("Format: White, Black")
        case _ => ()
    }
    btn

  private def buildImportJsonButton(manager: GameManager): Button =
    val button = new Button("Import JSON")
    button.setOnAction { _ =>
      val chooser = new FileChooser()
      chooser.setTitle("Import JSON File")
      chooser.getExtensionFilters.add(
        new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json")
      )
      val file = chooser.showOpenDialog(stage)
      if file != null then
        try
          val content = java.nio.file.Files.readString(
            java.nio.file.Paths.get(file.getAbsolutePath),
            java.nio.charset.StandardCharsets.UTF_8
          )
          Json.decode(content) match
            case Left(err) => msgLabel.setText(s"JSON error: $err")
            case Right(ctrl) =>
              manager.move(s"load ${Fen.encode(ctrl)}", this)
              selected = None
              currentCtrl = manager.state
              redrawBoard(currentCtrl)
              msgLabel.setText("Position loaded")
        catch
          case e: java.io.IOException => msgLabel.setText(s"JSON error: ${e.getMessage}")
    }
    button
  // $COVERAGE-ON$

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
            msgLabel.setText(s"Cannot read file: ${file.getPath}")
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
    selected = None
    currentCtrl = manager.state
    redrawBoard(currentCtrl)
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
