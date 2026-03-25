package de.eljachess.chess.gui

import de.eljachess.chess.controller.GameManager
import javafx.application.Application
import javafx.stage.Stage

object ChessApp:
  // Written by the main thread before Application.launch — @volatile ensures
  // the JavaFX Application Thread sees the assignment without a data race.
  @volatile var manager: GameManager = _

class ChessApp extends Application:
  override def start(stage: Stage): Unit =
    ChessGUI(ChessApp.manager, stage).show()
