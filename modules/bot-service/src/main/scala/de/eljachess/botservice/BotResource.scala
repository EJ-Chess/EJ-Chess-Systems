package de.eljachess.botservice

import de.eljachess.botservice.dto.{BotMoveRequest, BotMoveResponse}
import de.eljachess.chess.model.Color
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}

@Path("/bot")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class BotResource:

  /**
   * Compute the next move for the bot.
   *
   * Request body: { "fen": "...", "color": "white"|"black", "elo": 1400 }
   * Response:     { "from": "e2", "to": "e4" }
   */
  @POST
  @Path("/move")
  def getMove(req: BotMoveRequest): Response =
    val color = if req.color.toLowerCase == "black" then Color.Black else Color.White
    BotEngine.bestMove(req.fen, color, req.elo) match
      case Some((from, to)) =>
        Response.ok(BotMoveResponse(from, to)).build()
      case None =>
        Response
          .status(422)
          .entity(java.util.Map.of("error", "No legal moves available"))
          .build()
