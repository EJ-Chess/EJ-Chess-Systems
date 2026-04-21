package de.eljachess.chess.api.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for BotClient.
 *
 * The @CircuitBreaker / @Timeout / @Fallback annotations are CDI interceptors and
 * therefore only active inside a running Quarkus container (quarkusTest task).
 * Here we test the fallback method directly — it is the safety-net that all
 * fault-tolerance paths ultimately resolve to.
 */
class BotClientSpec extends AnyFlatSpec with Matchers:

  "BotClient.fetchMoveFallback" should "return None for a standard mid-game FEN" in {
    val client = BotClient()
    client.fetchMoveFallback(
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
      "black",
      1400
    ) shouldBe None
  }

  it should "return None for white with high ELO" in {
    val client = BotClient()
    client.fetchMoveFallback(
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      "white",
      2000
    ) shouldBe None
  }

  it should "return None for beginner ELO (800)" in {
    val client = BotClient()
    client.fetchMoveFallback("any-fen", "white", 800) shouldBe None
  }

  it should "always return None regardless of color or ELO" in {
    val client = BotClient()
    for
      color <- Seq("white", "black")
      elo   <- Seq(800, 1000, 1200, 1400, 1600, 1800, 2000)
    do
      client.fetchMoveFallback("some-fen", color, elo) shouldBe None
  }
