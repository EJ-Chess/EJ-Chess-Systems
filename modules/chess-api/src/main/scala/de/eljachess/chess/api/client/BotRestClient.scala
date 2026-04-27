package de.eljachess.chess.api.client

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

/** DTOs used by the MicroProfile REST Client. */
case class BotMoveDto(fen: String, color: String, elo: Int)
case class BotMoveResultDto(from: String, to: String)

/**
 * MicroProfile REST Client for bot-service.
 *
 * Quarkus automatically instruments this client with OpenTelemetry,
 * propagating the `traceparent` header so Jaeger can link
 * game-service → bot-service spans into a single distributed trace.
 *
 * Config:  quarkus.rest-client.bot-service.url (application.properties)
 * Docker:  BOT_SERVICE_URL env var (via bot-service.url MicroProfile alias)
 */
@RegisterRestClient(configKey = "bot-service")
@Path("/bot")
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
trait BotRestClient:
  @POST
  @Path("/move")
  def move(request: BotMoveDto): BotMoveResultDto
