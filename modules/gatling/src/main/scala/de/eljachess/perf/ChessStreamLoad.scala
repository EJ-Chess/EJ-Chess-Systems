package de.eljachess.perf

import cats.effect.{IO, IOApp}
import fs2.Stream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.http.HttpResponse.BodyHandlers

/**
 * fs2-based load generator for chess-api.
 *
 * Usage: ./gradlew :modules:gatling:runChessLoad -DloadN=100
 * Generates N concurrent game lifecycles (create → move → delete).
 */
object ChessStreamLoad extends IOApp.Simple:

  private val baseUrl = sys.props.getOrElse("baseUrl", "http://localhost:8080")
  private val N = sys.props.getOrElse("loadN", "100").toInt
  private val maxConcurrent = 50

  private val client: HttpClient = HttpClient.newHttpClient()

  def run: IO[Unit] =
    val startTime = IO.realTimeInstant

    val stream: Stream[IO, Either[Throwable, String]] =
      Stream.range(1, N + 1)
        .parEvalMap(maxConcurrent)(i => gameLifecycle(i).attempt)

    for
      t0      <- startTime
      results <- stream.compile.toList
      t1      <- IO.realTimeInstant
      _       <- IO.println(summary(results, t0, t1))
    yield ()

  private def gameLifecycle(i: Int): IO[String] =
    for
      gameId <- createGame()
      _      <- makeMove(gameId)
      _      <- deleteGame(gameId)
    yield s"[$i] done: $gameId"

  private def createGame(): IO[String] = IO.blocking {
    val req = HttpRequest.newBuilder()
      .uri(URI.create(s"$baseUrl/games"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString("{}"))
      .build()
    val resp = client.send(req, BodyHandlers.ofString())
    require(resp.statusCode() == 201, s"createGame failed: ${resp.statusCode()}")
    extractGameId(resp.body())
  }

  private def makeMove(gameId: String): IO[Unit] = IO.blocking {
    val body = """{"from":"e2","to":"e4"}"""
    val req = HttpRequest.newBuilder()
      .uri(URI.create(s"$baseUrl/games/$gameId/moves"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    val resp = client.send(req, BodyHandlers.ofString())
    require(resp.statusCode() == 200, s"makeMove failed: ${resp.statusCode()}")
  }

  private def deleteGame(gameId: String): IO[Unit] = IO.blocking {
    val req = HttpRequest.newBuilder()
      .uri(URI.create(s"$baseUrl/games/$gameId"))
      .DELETE()
      .build()
    val resp = client.send(req, BodyHandlers.ofString())
    require(resp.statusCode() == 204, s"deleteGame failed: ${resp.statusCode()}")
  }

  def extractGameId(json: String): String =
    val pattern = """"gameId"\s*:\s*"([^"]+)"""".r
    pattern.findFirstMatchIn(json)
      .map(_.group(1))
      .getOrElse(throw new RuntimeException(s"No gameId in: $json"))

  def summary(
    results: List[Either[Throwable, String]],
    t0: java.time.Instant,
    t1: java.time.Instant
  ): String =
    val ok = results.count(_.isRight)
    val err = results.count(_.isLeft)
    val ms = t1.toEpochMilli - t0.toEpochMilli
    s"Chess load complete: $ok/${results.length} OK, $err errors, ${ms}ms total"
