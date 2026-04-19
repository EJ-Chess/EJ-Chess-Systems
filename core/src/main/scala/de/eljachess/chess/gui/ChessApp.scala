package de.eljachess.chess.gui

import de.eljachess.chess.controller.GameManager
import javafx.application.Application
import javafx.stage.Stage

object ChessApp:
  @volatile var manager: GameManager = _
  @volatile var lookupManager: String => Either[String, GameManager] = _ => Left("No lookup")
  /** Optional setup hook: called before ChessGUI is shown.
    * chess-bot sets this to display a GameSetupScene before the board.
    */
  @volatile var setupHook: Stage => Unit = _ => ()

class ChessApp extends Application:
  override def start(stage: Stage): Unit =
    ChessApp.setupHook(stage)
    if ChessApp.manager != null then
      ChessGUI(ChessApp.manager, stage, ChessApp.lookupManager).show()
