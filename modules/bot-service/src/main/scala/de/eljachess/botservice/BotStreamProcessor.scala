package de.eljachess.botservice

import de.eljachess.botservice.dto.{BotMoveRequest, BotMoveResponse}
import de.eljachess.chess.model.Color
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.stream.QueueOfferResult
import scala.concurrent.{ExecutionContext, Future, Promise}

private case class QueueElement(
  request: BotMoveRequest,
  promise: Promise[Option[BotMoveResponse]]
)

/**
 * Bounded request queue for bot move computations with backpressure.
 *
 * Configured for tournament scenario: 12 teams × 50-60 moves/game = 6000+ concurrent requests.
 * When more than 500 requests arrive simultaneously, excess requests are dropped
 * (OverflowStrategy.dropNew), and the caller receives None, which maps to HTTP 503.
 *
 * Each request is processed by BotEngine.bestMove in parallel (parallelism = 8).
 * Parallelism tuned for modern 8-core CPUs.
 */
@ApplicationScoped
class BotStreamProcessor:

  private given system: ActorSystem = ActorSystem("bot-stream")
  private given ec: ExecutionContext = system.dispatcher

  private val (queue, _) =
    Source
      .queue[QueueElement](bufferSize = 500, OverflowStrategy.dropNew)
      .mapAsync(parallelism = 8) { elem =>
        Future {
          val color = if elem.request.color.toLowerCase == "black" then Color.Black else Color.White
          val result = BotEngine.bestMove(elem.request.fen, color, elem.request.elo)
            .map { case (from, to) => BotMoveResponse(from, to) }
          elem.promise.success(result)
          elem
        }
      }
      .toMat(Sink.ignore)(Keep.both)
      .run()

  /**
   * Enqueue a bot move request.
   *
   * Returns a Future that completes with:
   * - Some(BotMoveResponse) if the move was computed successfully
   * - None if the request was dropped (queue full) or no legal moves exist
   *
   * The future will complete within the timeout enforced by the REST caller.
   */
  def enqueue(req: BotMoveRequest): Future[Option[BotMoveResponse]] =
    val p = Promise[Option[BotMoveResponse]]()
    queue.offer(QueueElement(req, p)).map {
      case QueueOfferResult.Enqueued =>
        () // request is in the queue; the stream will complete the promise
      case _ =>
        p.success(None) // queue is full; immediately return None
    }
    p.future

  @PreDestroy
  def shutdown(): Unit =
    queue.complete()
    system.terminate()
