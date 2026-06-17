package de.eljachess.connector.client

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.eljachess.connector.dto.*
import jakarta.enterprise.context.ApplicationScoped

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets

/** Wraps all HTTP calls to the external tournament server (maichess).
 *
 *  Uses Java's built-in HttpClient so there are no extra runtime deps.
 *  A dedicated ObjectMapper is created here (not the Quarkus-managed one)
 *  because this client is also instantiated directly in unit tests. */
@ApplicationScoped
class TournamentHttpClient:

  private[client] val mapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private[client] val http: HttpClient = HttpClient.newHttpClient()

  // ── Auth ───────────────────────────────────────────────────────────────────

  def registerBot(serverUrl: String, name: String): Either[String, RegisterResponse] =
    val body = mapper.writeValueAsString(RegisterRequest(name, isBot = true))
    val req = post(s"$serverUrl/api/auth/register", body, auth = None)
    send(req) { resp =>
      if resp.statusCode() == 201 || resp.statusCode() == 200 then
        Right(mapper.readValue(resp.body(), classOf[RegisterResponse]))
      else
        Left(s"Registration failed: HTTP ${resp.statusCode()} — ${resp.body()}")
    }

  // ── Tournament discovery ───────────────────────────────────────────────────

  /** Returns all tournaments visible on the server (any status). */
  def listTournaments(serverUrl: String): Either[String, List[TournamentSummary]] =
    val req = get(s"$serverUrl/api/tournament", auth = None)
    send(req) { resp =>
      if resp.statusCode() == 200 then
        val tree = mapper.readTree(resp.body())
        val summaries: List[TournamentSummary] =
          if tree.isArray then
            mapper.readValue(resp.body(), new TypeReference[List[TournamentSummary]] {})
          else
            // Some servers return {created:[...], started:[...], finished:[...]}
            List("created", "started", "finished").flatMap { key =>
              val node = tree.get(key)
              if node != null && node.isArray then
                mapper.readValue(node.toString, new TypeReference[List[TournamentSummary]] {})
              else Nil
            }
        Right(summaries)
      else
        Left(s"List tournaments failed: HTTP ${resp.statusCode()} — ${resp.body()}")
    }

  // ── Participation ──────────────────────────────────────────────────────────

  def joinTournament(serverUrl: String, tournamentId: String, token: String): Either[String, Unit] =
    val req = post(s"$serverUrl/api/tournament/$tournamentId/join", body = "", auth = Some(token))
    send(req) { resp =>
      if resp.statusCode() == 200 then Right(())
      else Left(s"Join failed: HTTP ${resp.statusCode()} — ${resp.body()}")
    }

  // ── Move submission ────────────────────────────────────────────────────────

  def submitMove(
    serverUrl: String,
    tournamentId: String,
    gameId: String,
    uci: String,
    token: String
  ): Either[String, Unit] =
    val req = post(
      s"$serverUrl/api/tournament/$tournamentId/game/$gameId/move/$uci",
      body = "",
      auth = Some(token)
    )
    send(req) { resp =>
      if resp.statusCode() == 200 then Right(())
      else Left(s"Submit move $uci failed: HTTP ${resp.statusCode()} — ${resp.body()}")
    }

  // ── NDJSON streaming ───────────────────────────────────────────────────────

  /** Blocks the calling thread, invoking [onEvent] for every parsed line.
   *  Returns when the stream closes or an error occurs. */
  def streamTournamentEvents(
    serverUrl: String,
    tournamentId: String,
    token: String,
    onEvent: TournamentStreamEvent => Unit
  ): Unit =
    val req = streamGet(s"$serverUrl/api/tournament/$tournamentId/stream", token)
    streamLines(req) { line =>
      val event = mapper.readValue(line, classOf[TournamentStreamEvent])
      onEvent(event)
    }

  /** Blocks the calling thread, invoking [onEvent] for every parsed line. */
  def streamGameEvents(
    serverUrl: String,
    tournamentId: String,
    gameId: String,
    token: String,
    onEvent: GameStreamEvent => Unit
  ): Unit =
    val req = streamGet(s"$serverUrl/api/tournament/$tournamentId/game/$gameId/stream", token)
    streamLines(req) { line =>
      val event = mapper.readValue(line, classOf[GameStreamEvent])
      onEvent(event)
    }

  // ── Private helpers ────────────────────────────────────────────────────────

  private def get(url: String, auth: Option[String]): HttpRequest =
    val builder = HttpRequest.newBuilder().uri(URI.create(url)).GET()
    auth.foreach(t => builder.header("Authorization", s"Bearer $t"))
    builder.build()

  private def post(url: String, body: String, auth: Option[String]): HttpRequest =
    val builder = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
    auth.foreach(t => builder.header("Authorization", s"Bearer $t"))
    builder.build()

  private def streamGet(url: String, token: String): HttpRequest =
    HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Authorization", s"Bearer $token")
      .header("Accept", "application/x-ndjson")
      .GET()
      .build()

  private def send[A](req: HttpRequest)(f: HttpResponse[String] => Either[String, A]): Either[String, A] =
    try f(http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)))
    catch case e: Exception => Left(s"HTTP error: ${e.getMessage}")

  private def streamLines(req: HttpRequest)(handler: String => Unit): Unit =
    try
      val resp = http.send(req, HttpResponse.BodyHandlers.ofLines())
      resp.body().forEach { line =>
        if line != null && line.nonEmpty then
          try handler(line)
          catch case e: Exception =>
            println(s"[TournamentConnector] Failed to parse event line: $line — ${e.getMessage}")
      }
    catch case e: Exception =>
      println(s"[TournamentConnector] Stream error: ${e.getMessage}")
