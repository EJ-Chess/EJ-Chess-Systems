package de.eljachess.chess.api.service

import de.eljachess.chess.api.dto.CreateGameRequest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class GameLifecycleStreamSpec extends AnyFlatSpec with Matchers:

  private val stream = new GameLifecycleStream()

  // Instantiate a real GameService with no CDI injection
  // (botClient and repository will be null, which is guarded in GameService)
  stream.gameService = new GameService()

  "GameLifecycleStream.singleLifecycle" should "complete and return a string" in {
    val result = stream.singleLifecycle(1).unsafeRunSync()
    result should startWith("[1]")
  }

  "GameLifecycleStream.runBulk" should "return BulkGameResult with total=5, successful=5, failed=0 for count=5" in {
    val result = stream.runBulk(5).unsafeRunSync()
    result.total should equal(5)
    result.successful should equal(5)
    result.failed should equal(0)
  }

  it should "return all zeros when count=0" in {
    val result = stream.runBulk(0).unsafeRunSync()
    result.total should equal(0)
    result.successful should equal(0)
    result.failed should equal(0)
    result.durationMs should be >= 0L
  }

  it should "have successful count equal to total for small counts" in {
    val result = stream.runBulk(5).unsafeRunSync()
    result.successful should equal(result.total)
  }

  it should "complete without error for stress test (200 games)" in {
    val result = stream.runBulk(200).unsafeRunSync()
    result.total should equal(200)
    result.successful should equal(200)
    result.failed should equal(0)
  }
