package de.eljachess.chess.api.controller

import de.eljachess.chess.api.service.GameService
import de.eljachess.chess.api.dto.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import scala.compiletime.uninitialized

@Path("/games/{id}")
@Produces(Array(MediaType.APPLICATION_JSON))
class ExportController:

  @Inject
  var service: GameService = uninitialized

  @GET
  @Path("fen")
  def getFen(@PathParam("id") gameId: String): Response =
    service.getGameState(gameId) match
      case Right(state) =>
        Response.ok(FenResponse(gameId, state.fen)).build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND)
          .entity(ErrorResponse(error, gameId = Some(gameId)))
          .build()

  @GET
  @Path("pgn")
  def getPgn(@PathParam("id") gameId: String): Response =
    service.getPgn(gameId) match
      case Right(pgn) =>
        Response.ok(PgnResponse(gameId, pgn)).build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND)
          .entity(ErrorResponse(error, gameId = Some(gameId)))
          .build()
