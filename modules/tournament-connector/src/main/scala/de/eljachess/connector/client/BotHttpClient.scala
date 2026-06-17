package de.eljachess.connector.client

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.eljachess.connector.dto.{BotMoveRequest, BotMoveResponse}
import jakarta.enterprise.context.ApplicationScoped

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets

/** Calls our own bot-service (POST /bot/move) and converts the response
 *  to a UCI move string (from + to, e.g. "e2e4"). */
@ApplicationScoped
class BotHttpClient:

  private[client] val mapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private[client] val http: HttpClient = HttpClient.newHttpClient()

  /** Returns the best move in UCI notation, or None on error / no legal move. */
  def getBotMove(botServiceUrl: String, fen: String, color: String, elo: Int): Option[String] =
    try
      val body = mapper.writeValueAsString(BotMoveRequest(fen, color, elo))
      val req = HttpRequest.newBuilder()
        .uri(URI.create(s"$botServiceUrl/bot/move"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build()
      val resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      if resp.statusCode() == 200 then
        val move = mapper.readValue(resp.body(), classOf[BotMoveResponse])
        Some(move.toUci)
      else
        println(s"[BotHttpClient] Bot-service returned HTTP ${resp.statusCode()}: ${resp.body()}")
        None
    catch case e: Exception =>
      println(s"[BotHttpClient] Error calling bot-service: ${e.getMessage}")
      None
