package de.eljachess.chess.api.startup

import de.eljachess.chess.api.dto.CreateGameRequest
import de.eljachess.chess.api.service.GameService
import de.eljachess.chess.gui.{ChessApp, ChessGUI}
import de.eljachess.chess.tui.TUI
import de.eljachess.bot.{BotOpponent, GameSetupScene, HumanOpponent}
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
    // Show setup dialog before the board; game is created after user confirms
    ChessApp.setupHook = stage => {
      val setupStage = new javafx.stage.Stage()
      setupStage.setTitle("New Chess Game")

      val setupScene = new GameSetupScene(setup => {
        val colorStr = if setup.playerColor == de.eljachess.chess.model.Color.Black then "black" else "white"
        val (opponentStr, botElo) = setup.opponent match
          case HumanOpponent    => ("human", None)
          case BotOpponent(elo) => ("bot", Some(elo.elo))

        val request = CreateGameRequest(
          playerName  = Some(setup.playerName),
          playerColor = Some(colorStr),
          opponent    = Some(opponentStr),
          botElo      = botElo
        )

        val gameId = service.createGame(request)
        println(s"[Chess] Local game started: $gameId")

        service.getManager(gameId) match
          case Left(err) => System.err.println(s"[LocalUIStartup] $err")
          case Right(manager) =>
            val tuiThread = Thread(
              () => try TUI(manager).start() catch case _: Exception => (),
              "tui-thread"
            )
            tuiThread.setDaemon(true)
            tuiThread.start()

            ChessApp.manager       = manager
            ChessApp.lookupManager = service.getManager
            ChessGUI(manager, stage, service.getManager).show()
            setupStage.close()
      })

      setupStage.setScene(setupScene)
      setupStage.setOnCloseRequest(_ => System.exit(0))
      setupStage.show()
    }

    // GUI (JavaFX) in eigenem Thread
    val guiThread = Thread(
      () => Application.launch(classOf[ChessApp]),
      "gui-thread"
    )
    guiThread.setDaemon(true)
    guiThread.start()
