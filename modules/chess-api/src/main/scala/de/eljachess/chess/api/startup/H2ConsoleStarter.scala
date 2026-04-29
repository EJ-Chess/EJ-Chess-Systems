package de.eljachess.chess.api.startup

import io.quarkus.runtime.{LaunchMode, StartupEvent}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.h2.tools.Server

/**
 * Starts the H2 web console on port 8082 in DEV mode only.
 *
 * Access: http://localhost:8082
 *   JDBC URL: jdbc:h2:mem:chess
 *   User:     sa
 *   Password: (leer)
 */
@ApplicationScoped
class H2ConsoleStarter:

  private var h2Server: Server = _

  def onStart(@Observes event: StartupEvent, mode: LaunchMode): Unit =
    if mode == LaunchMode.DEVELOPMENT then
      h2Server = Server
        .createWebServer("-web", "-webAllowOthers", "-webPort", "8082")
        .start()
