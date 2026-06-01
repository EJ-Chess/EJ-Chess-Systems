package de.eljachess.chess.api.service

import de.eljachess.chess.api.dto.{BulkGameRequest, BulkGameResult, CreateGameRequest}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import cats.effect.IO
import fs2.Stream

/** fs2 stream processor for bulk game lifecycle operations.
 *
 * Enables running up to 500 game lifecycles simultaneously in production using bounded concurrency.
 * Each lifecycle: createGame → makeMove(e2→e4) → deleteGame.
 */
@ApplicationScoped
class GameLifecycleStream:

  /** Injected by CDI. */
  @Inject var gameService: GameService = uninitialized

  private val maxConcurrent = 50

  /** Run N game lifecycles in parallel (bounded to maxConcurrent).
   *
   * Returns a summary with total, successful, failed counts and duration.
   */
  def runBulk(count: Int): IO[BulkGameResult] =
    val start = IO.realTimeInstant
    Stream
      .range(1, count + 1)
      .parEvalMap(maxConcurrent)(i => singleLifecycle(i).attempt)
      .compile.toList
      .flatMap { results =>
        start.flatMap { t0 =>
          IO.realTimeInstant.map { t1 =>
            BulkGameResult(
              total      = count,
              successful = results.count(_.isRight),
              failed     = results.count(_.isLeft),
              durationMs = t1.toEpochMilli - t0.toEpochMilli
            )
          }
        }
      }

  /** Single game lifecycle: create → move → delete.
   *
   * Wrapped in IO.blocking because gameService methods are synchronous JVM code.
   */
  private[service] def singleLifecycle(i: Int): IO[String] =
    for
      gameId <- IO.blocking(gameService.createGame(CreateGameRequest()))
      _      <- IO.blocking(gameService.makeMoveAlgebraic(gameId, "e2", "e4", None))
      _      <- IO.blocking(gameService.deleteGame(gameId))
    yield s"[$i] $gameId"
