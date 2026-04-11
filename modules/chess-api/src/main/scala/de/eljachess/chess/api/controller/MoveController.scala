package de.eljachess.chess.api.controller

import de.eljachess.chess.api.service.GameService
import de.eljachess.chess.api.dto.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import scala.compiletime.uninitialized

@Path("/games/{id}")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class MoveController:

  @Inject
  var service: GameService = uninitialized

  @POST
  @Path("moves")
  def makeMove(@PathParam("id") gameId: String, req: MakeMoveRequest): Response =
    val result = (req.from, req.to, req.san) match
      case (Some(from), Some(to), _) =>
        service.makeMoveAlgebraic(gameId, from, to, req.promotion)
      case (_, _, Some(san)) =>
        service.makeMoveSan(gameId, san)
      case _ =>
        Left("Either (from, to) or san must be provided")

    result match
      case Right(_) =>
        Response.ok(MoveSuccessResponse()).build()
      case Left(error) =>
        val statusCode =
          if error.contains("not found") then Response.Status.NOT_FOUND
          else Response.Status.BAD_REQUEST
        service.getLegalMoves(gameId) match
          case Right(moves) =>
            val moveList = moves.map(m => s"${m.from}${m.to}")
            Response.status(statusCode)
              .entity(MoveErrorResponse(error = error, legalMoves = Some(moveList), gameId = Some(gameId)))
              .build()
          case Left(_) =>
            Response.status(statusCode)
              .entity(MoveErrorResponse(error = error, gameId = Some(gameId)))
              .build()

  @GET
  @Path("moves")
  def getLegalMoves(@PathParam("id") gameId: String): Response =
    service.getLegalMoves(gameId) match
      case Right(moves) =>
        service.getGameState(gameId) match
          case Right(state) =>
            Response.ok(LegalMovesResponse(
              gameId = gameId,
              currentTurn = state.currentTurn,
              legalMoves = moves,
              count = moves.size
            )).build()
          case Left(error) =>
            Response.status(Response.Status.NOT_FOUND)
              .entity(ErrorResponse(error, gameId = Some(gameId)))
              .build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND)
          .entity(ErrorResponse(error, gameId = Some(gameId)))
          .build()

  @POST
  @Path("undo")
  def undo(@PathParam("id") gameId: String): Response =
    service.undo(gameId) match
      case Right(fen) =>
        Response.ok(UndoRedoResponse(success = true, fen = fen)).build()
      case Left(error) =>
        val statusCode =
          if error.contains("not found") then Response.Status.NOT_FOUND
          else Response.Status.BAD_REQUEST
        Response.status(statusCode)
          .entity(UndoRedoErrorResponse(error = error))
          .build()

  @POST
  @Path("redo")
  def redo(@PathParam("id") gameId: String): Response =
    service.redo(gameId) match
      case Right(fen) =>
        Response.ok(UndoRedoResponse(success = true, fen = fen)).build()
      case Left(error) =>
        val statusCode =
          if error.contains("not found") then Response.Status.NOT_FOUND
          else Response.Status.BAD_REQUEST
        Response.status(statusCode)
          .entity(UndoRedoErrorResponse(error = error))
          .build()
