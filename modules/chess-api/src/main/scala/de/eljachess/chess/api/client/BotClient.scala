package de.eljachess.chess.api.client

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.faulttolerance.{CircuitBreaker, Fallback, Retry, Timeout}
import org.eclipse.microprofile.rest.client.inject.RestClient
import scala.compiletime.uninitialized

/**
 * CDI-managed HTTP client for the bot-service.
 *
 * Uses MicroProfile REST Client (BotRestClient) so that Quarkus OpenTelemetry
 * automatically propagates the `traceparent` header — enabling linked traces
 * in Jaeger across game-service → bot-service.
 *
 * Fault-tolerance stack (outer → inner per MicroProfile spec):
 *   @Retry          — 1 retry after 300 ms for transient network hiccups
 *   @CircuitBreaker — open after ≥75 % failures in a 4-request window; recover after 10 s
 *   @Timeout        — abort each attempt after 3 s
 *   @Fallback       — return None when all attempts fail or circuit is open
 */
@ApplicationScoped
class BotClient:

  @Inject
  @RestClient
  var restClient: BotRestClient = uninitialized

  @Retry(maxRetries = 1, delay = 300L)
  @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000L)
  @Timeout(3000L)
  @Fallback(fallbackMethod = "fetchMoveFallback")
  def fetchMove(fen: String, color: String, elo: Int): Option[(String, String)] =
    val resp = restClient.move(BotMoveDto(fen, color, elo))
    Some((resp.from, resp.to))

  /** Fallback: bot move unavailable — the game simply continues without a bot response. */
  def fetchMoveFallback(fen: String, color: String, elo: Int): Option[(String, String)] =
    None
