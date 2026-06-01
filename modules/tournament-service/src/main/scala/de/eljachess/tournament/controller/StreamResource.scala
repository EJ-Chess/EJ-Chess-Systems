package de.eljachess.tournament.controller

import de.eljachess.tournament.dto.Error
import de.eljachess.tournament.service.{TournamentService, TournamentStreamService}
import jakarta.inject.Inject
import jakarta.ws.rs.core.{Response, StreamingOutput}
import jakarta.ws.rs.{GET, HeaderParam, Path, PathParam, Produces}
import scala.compiletime.uninitialized
import java.io.{BufferedOutputStream, PrintWriter}

@Path("/api/tournament")
class StreamResource:
  @Inject var service: TournamentService = uninitialized
  @Inject var streamService: TournamentStreamService = uninitialized

  @GET
  @Path("{id}/stream")
  @Produces(Array("application/x-ndjson"))
  def streamTournamentEvents(
    @PathParam("id") id: String,
    @HeaderParam("Authorization") auth: String
  ): Response =
    extractBotId(auth) match
      case None => Response.status(401).entity(Error("missing or invalid token")).build()
      case Some(_) =>
        service.findTournament(id) match
          case None => Response.status(404).entity(Error("tournament not found")).build()
          case Some(_) =>
            val output: StreamingOutput = os =>
              val writer = new PrintWriter(new BufferedOutputStream(os))
              streamService.streamTo(id, writer)
              writer.close()
            Response.ok(output).build()

  // ── Helper methods ──────────────────────────────────────────────────────

  private def extractBotId(auth: String): Option[String] =
    Option(auth)
      .filter(_.startsWith("Bearer "))
      .map(_.stripPrefix("Bearer ").trim)
      .filter(_.nonEmpty)
