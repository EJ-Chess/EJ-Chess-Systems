package de.eljachess.botservice

import de.eljachess.botservice.dto.{BotMoveRequest, BotMoveResponse}
import de.eljachess.chess.model.Color
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import scala.compiletime.uninitialized
import scala.concurrent.Await
import scala.concurrent.duration.*

@Path("/bot")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class BotResource:

  @Inject
  var processor: BotStreamProcessor = uninitialized

  /**
   * Compute the next move for the bot using the Pekko Streams request queue.
   *
   * Request body: { "fen": "...", "color": "white"|"black", "elo": 1400 }
   * Response:
   *   200: { "from": "e2", "to": "e4" }
   *   503: { "error": "Service overloaded" } (queue full or no legal moves)
   */
  @POST
  @Path("/move")
  def getMove(req: BotMoveRequest): Response =
    Await.result(processor.enqueue(req), 5.seconds) match
      case Some(resp) =>
        Response.ok(resp).build()
      case None =>
        Response
          .status(503)
          .entity(java.util.Map.of("error", "Service overloaded"))
          .build()
