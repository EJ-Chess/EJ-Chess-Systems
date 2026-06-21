package de.eljachess.analytics

import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AnalyticsServiceSpec extends AnyFlatSpec with Matchers {

  // AnalyticsService is instantiated directly — no CDI needed for unit tests
  private lazy val service = new AnalyticsService()

  "AnalyticsService.computeAnalytics" should "return a non-empty result" in {
    val result = service.computeAnalytics()
    result.status shouldBe "DONE"
    result.runAt should not be empty
  }

  it should "compute victories per player (Charlie has most wins)" in {
    val result = service.computeAnalytics()
    result.victoriesPerPlayer should not be empty
    result.bestPlayer shouldBe "Charlie"
  }

  it should "include white in wins-per-color" in {
    val result = service.computeAnalytics()
    result.winsPerColor.map(_.color) should contain("white")
  }

  it should "compute avgEloBeatPerPlayer with positive values" in {
    val result = service.computeAnalytics()
    result.avgEloBeatPerPlayer should not be empty
    result.avgEloBeatPerPlayer.foreach(_.avgEloBeat should be > 0.0)
  }

  it should "report IDLE status before any run" in {
    val fresh = new AnalyticsService()
    fresh.getResult.status shouldBe "IDLE"
  }

  // ── PGN parser tests ────────────────────────────────────────────────────────

  "AnalyticsService.parsePgn" should "parse two rows per game (one per player)" in {
    val pgn = """[White "Alice"]
[Black "Bob"]
[Result "1-0"]
[WhiteElo "1600"]
[BlackElo "1400"]

1. e4 e5 2. Nf3 Nc6 3. Bb5 1-0

[White "Charlie"]
[Black "Diana"]
[Result "0-1"]
[WhiteElo "1500"]
[BlackElo "1700"]

1. d4 d5 2. c4 0-1
"""
    val tmp = java.io.File.createTempFile("test", ".pgn")
    try {
      java.nio.file.Files.writeString(tmp.toPath, pgn)
      val rows = service.parsePgn(tmp.getAbsolutePath)
      rows should have size 4
      rows.map(_.playerName).toSet shouldBe Set("Alice", "Bob", "Charlie", "Diana")
    } finally tmp.delete()
  }

  it should "set winner correctly" in {
    val pgn = """[White "A"]
[Black "B"]
[Result "0-1"]
[WhiteElo "1000"]
[BlackElo "1200"]

1. e4 e5 0-1
"""
    val tmp = java.io.File.createTempFile("test", ".pgn")
    try {
      java.nio.file.Files.writeString(tmp.toPath, pgn)
      val rows = service.parsePgn(tmp.getAbsolutePath)
      rows.find(_.playerName == "B").map(_.winner) shouldBe Some("black")
      rows.find(_.playerName == "A").map(_.winner) shouldBe Some("black")
    } finally tmp.delete()
  }

  it should "skip games with unknown result (*)" in {
    val pgn = """[White "X"]
[Black "Y"]
[Result "*"]

1. e4 *
"""
    val tmp = java.io.File.createTempFile("test", ".pgn")
    try {
      java.nio.file.Files.writeString(tmp.toPath, pgn)
      service.parsePgn(tmp.getAbsolutePath) shouldBe empty
    } finally tmp.delete()
  }

  it should "set opponent ELO as botElo" in {
    val pgn = """[White "W"]
[Black "B"]
[Result "1-0"]
[WhiteElo "1800"]
[BlackElo "1300"]

1. e4 1-0
"""
    val tmp = java.io.File.createTempFile("test", ".pgn")
    try {
      java.nio.file.Files.writeString(tmp.toPath, pgn)
      val rows = service.parsePgn(tmp.getAbsolutePath)
      rows.find(_.playerName == "W").map(_.botElo) shouldBe Some(1300) // black ELO
      rows.find(_.playerName == "B").map(_.botElo) shouldBe Some(1800) // white ELO
    } finally tmp.delete()
  }
}
