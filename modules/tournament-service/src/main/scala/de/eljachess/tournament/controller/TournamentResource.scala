package de.eljachess.tournament.controller

import de.eljachess.tournament.auth.JwtHandler
import de.eljachess.tournament.dto.{CreateTournamentRequest, Tournament, TournamentInfo, Ok, Error}
import de.eljachess.tournament.service.TournamentService
import jakarta.inject.Inject
import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.{Consumes, DELETE, GET, HeaderParam, POST, Path, PathParam, Produces}
import scala.compiletime.uninitialized

@Path("/api/tournament")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class TournamentResource:
  @Inject var service: TournamentService = uninitialized

  @GET
  def listTournaments(): Response =
    val grouped = service.listTournaments()
    Response.ok(grouped).build()

  @POST
  def createTournament(
    @HeaderParam("Authorization") auth: String,
    req: CreateTournamentRequest
  ): Response =
    JwtHandler.extractUserId(auth) match
      case None => Response.status(401).entity(Error("missing or invalid token")).build()
      case Some(directorId) =>
        service.createTournament(req, directorId) match
          case Right(t) => Response.status(201).entity(t).build()
          case Left(e)  => handleError(e)

  @GET
  @Path("{id}")
  def getTournament(@PathParam("id") id: String): Response =
    service.findTournament(id) match
      case Some(t) => Response.ok(t).build()
      case None    => Response.status(404).entity(Error("tournament not found")).build()

  @DELETE
  @Path("{id}")
  def deleteTournament(
    @PathParam("id") id: String,
    @HeaderParam("Authorization") auth: String
  ): Response =
    JwtHandler.extractUserId(auth) match
      case None => Response.status(401).entity(Error("missing or invalid token")).build()
      case Some(directorId) =>
        service.findTournament(id) match
          case None => Response.status(404).entity(Error("tournament not found")).build()
          case Some(t) =>
            if t.createdBy != directorId then
              Response.status(403).entity(Error("only director can delete")).build()
            else if t.status != "created" then
              Response.status(409).entity(Error("can only delete tournaments in created status")).build()
            else
              Response.noContent().build()

  @POST
  @Path("{id}/start")
  def startTournament(
    @PathParam("id") id: String,
    @HeaderParam("Authorization") auth: String
  ): Response =
    JwtHandler.extractUserId(auth) match
      case None => Response.status(401).entity(Error("missing or invalid token")).build()
      case Some(directorId) =>
        service.startTournament(id, directorId) match
          case Right(t) => Response.ok(t).build()
          case Left(e)  => handleError(e)

  // ── Helper methods ──────────────────────────────────────────────────────

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
