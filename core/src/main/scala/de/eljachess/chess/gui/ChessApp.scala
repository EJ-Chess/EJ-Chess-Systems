package de.eljachess.chess.gui

import de.eljachess.chess.controller.GameManager
import javafx.application.Application
import javafx.stage.Stage

object ChessApp:
  @volatile var manager: GameManager = _
  @volatile var lookupManager: String => Either[String, GameManager] = _ => Left("No lookup")

class ChessApp extends Application:
  override def start(stage: Stage): Unit =
    ChessGUI(ChessApp.manager, stage, ChessApp.lookupManager).show()
