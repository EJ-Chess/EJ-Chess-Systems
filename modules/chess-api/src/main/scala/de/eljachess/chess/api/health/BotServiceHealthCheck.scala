package de.eljachess.chess.api.health

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.health.{HealthCheck, HealthCheckResponse, Readiness}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/**
 * Readiness probe: checks whether the bot-service is reachable.
 *
 * Exposed at /q/health/ready as "bot-service".
 * When bot-service is DOWN the game-service also reports READY=false,
 * which prevents docker-compose from routing traffic before dependencies are up.
 *
 * `targetUrl` is protected so unit tests can override it without a running Quarkus context.
 */
@Readiness
@ApplicationScoped
class BotServiceHealthCheck extends HealthCheck:

  /** URL to probe. Overridable in tests via a subclass. */
  protected def targetUrl: String =
    try
      org.eclipse.microprofile.config.ConfigProvider
        .getConfig()
        .getOptionalValue("bot-service.url", classOf[String])
        .orElse("http://localhost:8081")
    catch
      case _: Exception => "http://localhost:8081"

  private val httpClient = HttpClient.newHttpClient()

  override def call(): HealthCheckResponse =
    try
      val request = HttpRequest.newBuilder()
        .uri(URI.create(s"$targetUrl/q/health/ready"))
        .GET()
        .build()
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if response.statusCode() == 200 then
        HealthCheckResponse.up("bot-service")
      else
        HealthCheckResponse
          .named("bot-service")
          .down()
          .withData("httpStatus", response.statusCode().toLong)
          .build()
    catch
      case ex: Exception =>
        HealthCheckResponse
          .named("bot-service")
          .down()
          .withData("error", ex.getMessage)
          .build()
