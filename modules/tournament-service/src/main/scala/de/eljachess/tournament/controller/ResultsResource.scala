package de.eljachess.tournament.controller

import de.eljachess.tournament.dto.Error
import de.eljachess.tournament.service.TournamentService
import jakarta.inject.Inject
import jakarta.ws.rs.core.{MediaType, Response, StreamingOutput}
import jakarta.ws.rs.{GET, HeaderParam, Path, PathParam, Produces, QueryParam}
import scala.compiletime.uninitialized
import java.io.{BufferedOutputStream, PrintWriter}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

@Path("/api/tournament")
class ResultsResource:
  @Inject var service: TournamentService = uninitialized

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  @GET
  @Path("{id}/results")
  @Produces(Array("application/x-ndjson"))
  def getResults(
    @PathParam("id") id: String,
    @QueryParam("nb") nb: Integer
  ): Response =
    service.findTournament(id) match
      case None => Response.status(404).entity(Error("tournament not found")).build()
      case Some(_) =>
        val output: StreamingOutput = os =>
          val writer = new PrintWriter(new BufferedOutputStream(os))
          val standings = service.getStandings(id)
          val limit = if nb != null && nb > 0 then nb.toInt else Int.MaxValue
          standings.take(limit).foreach(r => writer.println(mapper.writeValueAsString(r)))
          writer.flush()
          writer.close()
        Response.ok(output).build()

  @GET
  @Path("{id}/round/{round}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getRoundPairings(
    @PathParam("id") id: String,
    @PathParam("round") round: Int
  ): Response =
    service.findTournament(id) match
      case None => Response.status(404).entity(Error("tournament not found")).build()
      case Some(_) =>
        val pairings = service.getRoundPairings(id, round)
        val body = scala.collection.immutable.Map(
          "round" -> round,
          "pairings" -> pairings
        )
        Response.ok(body).build()

  @GET
  @Path("{id}/export/games")
  def exportGames(
    @PathParam("id") id: String,
    @HeaderParam("Accept") accept: String
  ): Response =
    service.findTournament(id) match
      case None => Response.status(404).entity(Error("tournament not found")).build()
      case Some(_) =>
        val contentType = if accept == null || accept.isEmpty then "application/x-chess-pgn" else accept
        if contentType.contains("ndjson") then
          exportGamesAsNDJSON(id)
        else
          exportGamesAsPGN(id)

  private def exportGamesAsNDJSON(id: String): Response =
    val output: StreamingOutput = os =>
      val writer = new PrintWriter(new BufferedOutputStream(os))
      // Placeholder: export games as NDJSON
      // In real implementation, would query all games for tournament and output GameExport objects
      writer.flush()
      writer.close()
    Response.ok(output).header("Content-Type", "application/x-ndjson").build()

  private def exportGamesAsPGN(id: String): Response =
    val output: StreamingOutput = os =>
      val writer = new PrintWriter(new BufferedOutputStream(os))
      // Placeholder: export games as PGN
      // In real implementation, would query all games and output PGN blocks
      writer.flush()
      writer.close()
    Response.ok(output).header("Content-Type", "application/x-chess-pgn").build()
