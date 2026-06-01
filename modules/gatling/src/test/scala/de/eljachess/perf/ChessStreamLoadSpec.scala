package de.eljachess.perf

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

class ChessStreamLoadSpec extends AnyFlatSpec with Matchers:

  implicit val ec: ExecutionContext = ExecutionContext.global

  "ChessStreamLoad.extractGameId" should "extract gameId from valid JSON" in {
    val json = """{"gameId":"abc-123-def","fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}"""
    val result = ChessStreamLoad.extractGameId(json)
    result should equal("abc-123-def")
  }

  it should "throw RuntimeException if gameId is missing" in {
    val json = """{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}"""
    assertThrows[RuntimeException] {
      ChessStreamLoad.extractGameId(json)
    }
  }

  it should "throw RuntimeException if JSON is empty" in {
    val json = """{}"""
    assertThrows[RuntimeException] {
      ChessStreamLoad.extractGameId(json)
    }
  }

  "ChessStreamLoad.summary" should "report correct counts for mixed results" in {
    val results = List(
      Right("ok-1"),
      Right("ok-2"),
      Left(new RuntimeException("fail-1"))
    )
    val t0 = java.time.Instant.now()
    val t1 = t0.plusSeconds(1)

    val summary = ChessStreamLoad.summary(results, t0, t1)

    summary should include("2")  // 2 OK
    summary should include("1")  // 1 error
    summary should include("1000") // ~1000ms
  }

  it should "report all OK when no errors occur" in {
    val results = List(Right("ok-1"), Right("ok-2"), Right("ok-3"))
    val t0 = java.time.Instant.now()
    val t1 = t0.plusMillis(100)

    val summary = ChessStreamLoad.summary(results, t0, t1)

    summary should include("3/3 OK")
    summary should include("0 errors")
  }

  "fs2 stream integration" should "compile and run basic stream operations" in {
    val result = Stream.range(1, 4).compile.toList
    result.should(equal(List(1, 2, 3)))
  }

  it should "support map operations" in {
    val result = Stream.emits(List(1, 2, 3))
      .map(_ * 2)
      .compile.toList
    result.should(equal(List(2, 4, 6)))
  }

  it should "support parEvalMap with IO" in {
    val result = Stream.range(1, 4)
      .parEvalMap(2)(i => IO.pure(i * 2))
      .compile.toList.unsafeRunSync()
    result.sorted.should(equal(List(2, 4, 6)))  // order may vary due to parallelism
  }
