// $COVERAGE-OFF$
package de.eljachess.bot

import de.eljachess.chess.controller.GameManager
import de.eljachess.chess.gui.{ChessApp, ChessGUI}
import de.eljachess.chess.tui.TUI
import javafx.application.Application
import javafx.stage.Stage
import java.io.PrintStream

@main def botMain(): Unit =
  System.setOut(PrintStream(System.out, true, "UTF-8"))

  // Register the GUI setup hook: show GameSetupScene before board
  ChessApp.setupHook = (stage: Stage) => {
    val setupStage = new javafx.stage.Stage()
    setupStage.setTitle("New Chess Game")

    val setupScene = new GameSetupScene(setup => {
      val controller = GameFactory.createGame(setup)
      val manager    = GameManager(controller)

      ChessApp.manager = manager

      // Start TUI in daemon thread
      val tui = TUI(manager)
      val tuiThread = Thread(() => tui.start(), "tui-thread")
      tuiThread.setDaemon(true)
      tuiThread.start()

      // Switch to board
      ChessGUI(manager, stage, ChessApp.lookupManager).show()
      setupStage.close()
    })

    setupStage.setScene(setupScene)
    setupStage.setOnCloseRequest(_ => System.exit(0))
    setupStage.show()
  }

  Application.launch(classOf[ChessApp])
// $COVERAGE-ON$
