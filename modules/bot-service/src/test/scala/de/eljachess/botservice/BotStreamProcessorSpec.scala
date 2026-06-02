package de.eljachess.botservice

import de.eljachess.botservice.dto.{BotMoveRequest, BotMoveResponse}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Await
import scala.concurrent.duration.*

class BotStreamProcessorSpec extends AnyFlatSpec with Matchers:

  private val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  "BotStreamProcessor.enqueue" should "return Some(BotMoveResponse) for a valid request" in {
    val processor = BotStreamProcessor()
    val req = BotMoveRequest(fen = initialFen, color = "white", elo = 1400)

    val result = Await.result(processor.enqueue(req), 5.seconds)

    result should not be empty
    result.get should be(a[BotMoveResponse])
    result.get.from should have length 2
    result.get.to should have length 2

    processor.shutdown()
  }

  it should "complete the future within a reasonable time" in {
    val processor = BotStreamProcessor()
    val req = BotMoveRequest(fen = initialFen, color = "black", elo = 1800)

    val startTime = System.currentTimeMillis()
    val result = Await.result(processor.enqueue(req), 5.seconds)
    val elapsed = System.currentTimeMillis() - startTime

    result should not be empty
    elapsed should be < 2000L // should complete in under 2 seconds

    processor.shutdown()
  }

  it should "return None for an invalid FEN" in {
    val processor = BotStreamProcessor()
    val req = BotMoveRequest(fen = "not-a-valid-fen", color = "white", elo = 1400)

    val result = Await.result(processor.enqueue(req), 5.seconds)

    result should be(empty)

    processor.shutdown()
  }

  it should "drop requests when the queue is full (buffer size = 500)" in {
    val processor = BotStreamProcessor()
    val req = BotMoveRequest(fen = initialFen, color = "white", elo = 1400)

    // Flood the queue with more requests than the buffer can hold
    val futures = (1 to 600).map(_ => processor.enqueue(req))

    // Wait for all to complete
    val results = futures.map(f => Await.result(f, 5.seconds))

    // At least some should be dropped (None)
    val dropCount = results.count(_.isEmpty)
    dropCount should be > 0

    processor.shutdown()
  }

  it should "shutdown gracefully without throwing exceptions" in {
    val processor = BotStreamProcessor()
    val req = BotMoveRequest(fen = initialFen, color = "white", elo = 1400)

    // Send a request
    val future = processor.enqueue(req)
    Await.result(future, 5.seconds)

    // Shutdown should not throw
    noException should be thrownBy processor.shutdown()
  }

  it should "preserve color case-insensitivity (white vs white)" in {
    val processor = BotStreamProcessor()
    val req = BotMoveRequest(fen = initialFen, color = "White", elo = 1400)

    val result = Await.result(processor.enqueue(req), 5.seconds)

    result should not be empty

    processor.shutdown()
  }

  it should "preserve color case-insensitivity (black vs BLACK)" in {
    val processor = BotStreamProcessor()
    val blackFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val req = BotMoveRequest(fen = blackFen, color = "BLACK", elo = 1400)

    val result = Await.result(processor.enqueue(req), 5.seconds)

    result should not be empty

    processor.shutdown()
  }
