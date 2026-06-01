package de.eljachess.tournament.controller

import de.eljachess.tournament.dto.{Ok, Error}
import de.eljachess.tournament.service.TournamentService
import jakarta.inject.Inject
import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.{Consumes, HeaderParam, POST, Path, PathParam, Produces}
import scala.compiletime.uninitialized

@Path("/api/tournament")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class ParticipationResource:
  @Inject var service: TournamentService = uninitialized

  @POST
  @Path("{id}/join")
  def joinTournament(
    @PathParam("id") id: String,
    @HeaderParam("Authorization") auth: String
  ): Response =
    extractBotId(auth) match
      case None => Response.status(401).entity(Error("missing or invalid token")).build()
      case Some(botId) =>
        service.joinTournament(id, botId, botId) match
          case Right(_) => Response.ok(Ok()).build()
          case Left(e)  => handleError(e)

  @POST
  @Path("{id}/withdraw")
  def withdrawFromTournament(
    @PathParam("id") id: String,
    @HeaderParam("Authorization") auth: String
  ): Response =
    extractBotId(auth) match
      case None => Response.status(401).entity(Error("missing or invalid token")).build()
      case Some(botId) =>
        service.withdrawFromTournament(id, botId) match
          case Right(_) => Response.ok(Ok()).build()
          case Left(e)  => handleError(e)

  // ── Helper methods ──────────────────────────────────────────────────────

  private def extractBotId(auth: String): Option[String] =
    Option(auth)
      .filter(_.startsWith("Bearer "))
      .map(_.stripPrefix("Bearer ").trim)
      .filter(_.nonEmpty)

  private def handleError(error: String): Response =
    if error.startsWith("400:") then
      Response.status(400).entity(Error(error.stripPrefix("400:").trim)).build()
    else if error.startsWith("401:") then
      Response.status(401).entity(Error(error.stripPrefix("401:").trim)).build()
    else if error.startsWith("403:") then
      Response.status(403).entity(Error(error.stripPrefix("403:").trim)).build()
    else if error.startsWith("404:") then
      Response.status(404).entity(Error(error.stripPrefix("404:").trim)).build()
    else if error.startsWith("409:") then
      Response.status(409).entity(Error(error.stripPrefix("409:").trim)).build()
    else
      Response.status(500).entity(Error(error)).build()
