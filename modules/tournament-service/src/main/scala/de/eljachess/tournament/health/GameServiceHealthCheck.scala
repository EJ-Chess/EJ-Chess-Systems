package de.eljachess.tournament.health

import org.eclipse.microprofile.health.{HealthCheck, HealthCheckResponse, Readiness}
import jakarta.enterprise.context.ApplicationScoped
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI

@Readiness
@ApplicationScoped
class GameServiceHealthCheck extends HealthCheck:
  private val http = HttpClient.newHttpClient()
  protected val targetUrl = "http://localhost:8080/q/health/ready"

  def call(): HealthCheckResponse =
    try
      val req = HttpRequest.newBuilder()
        .uri(URI.create(targetUrl))
        .timeout(java.time.Duration.ofSeconds(2))
        .GET()
        .build()
      val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
      if resp.statusCode() == 200 then
        HealthCheckResponse.up("game-service")
      else
        HealthCheckResponse
          .builder()
          .name("game-service")
          .status(false)
          .withData("reason", s"HTTP ${resp.statusCode()}")
          .build()
    catch
      case e: Exception =>
        HealthCheckResponse
          .builder()
          .name("game-service")
          .status(false)
          .withData("error", e.getMessage)
          .build()
