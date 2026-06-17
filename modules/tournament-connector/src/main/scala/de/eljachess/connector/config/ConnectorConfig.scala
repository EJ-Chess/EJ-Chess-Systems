package de.eljachess.connector.config

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import scala.compiletime.uninitialized

@ApplicationScoped
class ConnectorConfig:

  @ConfigProperty(name = "connector.tournament.server.url")
  var serverUrl: String = uninitialized

  @ConfigProperty(name = "connector.bot.name")
  var botName: String = uninitialized

  @ConfigProperty(name = "connector.bot.elo", defaultValue = "1400")
  var botElo: Int = 0

  @ConfigProperty(name = "connector.bot.service.url")
  var botServiceUrl: String = uninitialized

  /** If present, join this specific tournament ID. If absent, auto-join first available. */
  @ConfigProperty(name = "connector.tournament.id")
  var tournamentId: java.util.Optional[String] = uninitialized

  @ConfigProperty(name = "connector.poll.interval.seconds", defaultValue = "30")
  var pollIntervalSeconds: Int = 0

  @ConfigProperty(name = "connector.enabled", defaultValue = "true")
  var enabled: Boolean = false
