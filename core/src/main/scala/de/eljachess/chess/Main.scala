package de.eljachess.chess

import de.eljachess.chess.controller.{GameController, GameManager}
import de.eljachess.chess.gui.ChessApp
import de.eljachess.chess.model.Board
import de.eljachess.chess.tui.TUI
import java.io.PrintStream
import javafx.application.Application

@main def main(): Unit =
  System.setOut(PrintStream(System.out, true, "UTF-8"))
  val manager = GameManager(GameController(Board.initial))
  val tui     = TUI(manager)

  // TUI runs as a daemon thread: it dies automatically when the GUI closes.
  val tuiThread = Thread(() => tui.start(), "tui-thread")
  tuiThread.setDaemon(true)
  tuiThread.start()

  // The manager reference must be set BEFORE Application.launch is called.
  // @volatile on ChessApp.manager ensures the JavaFX thread sees the write.
  ChessApp.manager = manager
  Application.launch(classOf[ChessApp])
