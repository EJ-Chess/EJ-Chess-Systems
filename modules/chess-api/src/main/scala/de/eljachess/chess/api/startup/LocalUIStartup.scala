package de.eljachess.chess.api.startup

import de.eljachess.chess.api.service.GameService
import de.eljachess.chess.gui.ChessApp
import de.eljachess.chess.tui.TUI
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import javafx.application.Application
import scala.compiletime.uninitialized

@ApplicationScoped
class LocalUIStartup:

  @Inject
  var service: GameService = uninitialized

  def onStart(@Observes event: StartupEvent): Unit =
    val gameId = service.createGame()
    println(s"[Chess] Local game started: $gameId")
    service.getManager(gameId) match
      case Left(err) => System.err.println(s"[LocalUIStartup] $err")
      case Right(manager) =>
        // TUI in eigenem Thread (blockiert stdin)
        val tuiThread = Thread(
          () =>
            try TUI(manager).start()
            catch case _: Exception => () // kein Terminal vorhanden
          ,
          "tui-thread"
        )
        tuiThread.setDaemon(true)
        tuiThread.start()

        // GUI (JavaFX) in eigenem Thread
        val guiThread = Thread(
          () =>
            ChessApp.manager       = manager
            ChessApp.lookupManager = service.getManager
            Application.launch(classOf[ChessApp])
          ,
          "gui-thread"
        )
        guiThread.setDaemon(true)
        guiThread.start()
