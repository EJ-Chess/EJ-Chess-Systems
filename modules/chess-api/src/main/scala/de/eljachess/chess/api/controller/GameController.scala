package de.eljachess.chess.api.controller

import de.eljachess.chess.api.service.GameService
import de.eljachess.chess.api.dto.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import scala.compiletime.uninitialized

@Path("/games")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class GameController:

  @Inject
  var service: GameService = uninitialized

  @POST
  def createGame(req: CreateGameRequest): Response =
    val gameId = service.createGame(req)
    service.getGameState(gameId) match
      case Right(state) =>
        Response.status(Response.Status.CREATED)
          .entity(GameCreatedResponse(gameId, state.fen))
          .build()
      case Left(error) =>
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ErrorResponse(error))
          .build()

  @GET
  @Path("{id}")
  def getGame(@PathParam("id") gameId: String): Response =
    service.getGameState(gameId) match
      case Right(state) => Response.ok(state).build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND)
          .entity(ErrorResponse(error, gameId = Some(gameId)))
          .build()

  @DELETE
  @Path("{id}")
  def deleteGame(@PathParam("id") gameId: String): Response =
    service.deleteGame(gameId) match
      case Right(_) => Response.noContent().build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND)
          .entity(ErrorResponse(error, gameId = Some(gameId)))
          .build()

  @GET
  @Path("analytics-export")
  def analyticsExport(): Response =
    Response.ok(service.getAnalyticsData()).build()

  @POST
  @Path("{id}/import")
  def importGame(@PathParam("id") gameId: String, req: ImportRequest): Response =
    val result = (req.pgn, req.fen) match
      case (Some(pgn), _) => service.importPgn(gameId, pgn)
      case (_, Some(fen)) => service.importFen(gameId, fen)
      case _              => Left("Either pgn or fen must be provided")

    result match
      case Right(_) =>
        service.getGameState(gameId) match
          case Right(state) =>
            Response.ok(ImportResponse(success = true, fen = Some(state.fen))).build()
          case Left(error) =>
            Response.status(Response.Status.NOT_FOUND)
              .entity(ErrorResponse(error, gameId = Some(gameId)))
              .build()
      case Left(error) =>
        if error.contains("not found") then
          Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse(error, gameId = Some(gameId)))
            .build()
        else
          Response.status(Response.Status.BAD_REQUEST)
            .entity(ImportResponse(success = false, error = Some(error)))
            .build()
