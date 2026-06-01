package de.eljachess.chess.api.controller

import de.eljachess.chess.api.dto.{BulkGameRequest, BulkGameResult}
import de.eljachess.chess.api.service.GameLifecycleStream
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.{Consumes, POST, Path, Produces}
import scala.compiletime.uninitialized

/** Bulk game lifecycle operations via fs2 streaming.
 *
 * POST /games/bulk — Run up to 500 game lifecycles in parallel.
 */
@Path("/games")
@ApplicationScoped
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class BulkGameController:

  @Inject var lifecycleStream: GameLifecycleStream = uninitialized

  @POST
  @Path("/bulk")
  def runBulk(req: BulkGameRequest): Response =
    import cats.effect.unsafe.implicits.global
    val result = lifecycleStream.runBulk(req.count).unsafeRunSync()
    Response.ok(result).build()
