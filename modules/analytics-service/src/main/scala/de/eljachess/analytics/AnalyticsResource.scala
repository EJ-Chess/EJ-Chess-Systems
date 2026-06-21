package de.eljachess.analytics

import jakarta.inject.Inject
import jakarta.ws.rs._
import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.DefaultValue

@Path("/analytics")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class AnalyticsResource {

  @Inject
  var service: AnalyticsService = _

  /** Start the Spark analytics job asynchronously. Returns 202 immediately.
   *  @param source "local" (default) or "lichess" */
  @POST
  @Path("/run")
  def run(@QueryParam("source") @DefaultValue("local") source: String): Response = {
    service.runAsync(source)
    Response.accepted(Map("message" -> "Analytics job started")).build()
  }

  /** Return the current job status: IDLE, RUNNING, DONE, or ERROR. */
  @GET
  @Path("/status")
  def status(): Response =
    Response.ok(Map("status" -> service.getResult.status)).build()

  /** Return the full analytics result (includes status field). */
  @GET
  @Path("/results")
  def results(): Response =
    Response.ok(service.getResult).build()
}
