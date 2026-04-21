package de.eljachess.chess.api.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.faulttolerance.{CircuitBreaker, Fallback, Retry, Timeout}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/**
 * CDI-managed HTTP client for the bot-service.
 *
 * Fault-tolerance layers (applied via CDI interceptors when running in Quarkus):
 *   @Timeout       — abort the HTTP call after 3 s
 *   @CircuitBreaker — open the circuit after ≥75% failures in a 4-request window;
 *                    wait 10 s before trying again (half-open)
 *   @Fallback      — return None when the circuit is open or a timeout fires,
 *                    so the game continues without a bot response
 */
@ApplicationScoped
class BotClient:

  private lazy val botServiceUrl: String =
    try
      org.eclipse.microprofile.config.ConfigProvider
        .getConfig()
        .getOptionalValue("bot-service.url", classOf[String])
        .orElse("http://localhost:8081")
    catch
      case _: Exception => "http://localhost:8081"

  private val httpClient = HttpClient.newHttpClient()
  private val jsonMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  /**
   * Ask the bot-service for the best move given a FEN position, side to move, and ELO.
   *
   * Fault-tolerance stack (outer → inner per MicroProfile spec):
   *   @Retry          — 1 retry after 300 ms for transient network hiccups
   *   @CircuitBreaker — open after ≥75 % failures in a 4-request window; recover after 10 s
   *   @Timeout        — abort each individual attempt after 3 s
   *   @Fallback       — return None when all attempts fail or circuit is open
   */
  @Retry(maxRetries = 1, delay = 300L)
  @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000L)
  @Timeout(3000L)
  @Fallback(fallbackMethod = "fetchMoveFallback")
  def fetchMove(fen: String, color: String, elo: Int): Option[(String, String)] =
    val body = jsonMapper.writeValueAsString(
      Map("fen" -> fen, "color" -> color, "elo" -> elo)
    )
    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$botServiceUrl/bot/move"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode() == 200 then
      val tree = jsonMapper.readTree(response.body())
      Some((tree.get("from").asText(), tree.get("to").asText()))
    else
      throw RuntimeException(s"Bot service error: HTTP ${response.statusCode()}")

  /** Fallback: bot move unavailable — the game simply continues without a bot response. */
  def fetchMoveFallback(fen: String, color: String, elo: Int): Option[(String, String)] =
    None
