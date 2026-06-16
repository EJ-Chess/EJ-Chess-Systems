package de.eljachess.chess.api.kafka

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * Unit tests for BotMoveKafkaProducer's serialisation logic.
 *
 * We test the JSON payload shape without a live Kafka broker by
 * inspecting the ObjectMapper output directly.
 */
class BotMoveKafkaProducerSpec extends AnyFlatSpec with Matchers:

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  // ── BotMoveRequestEvent serialisation ──────────────────────────────────────

  "BotMoveRequestEvent" should "serialise all fields to JSON" in {
    val event = BotMoveRequestEvent(
      gameId = "abc-123",
      fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      color  = "black",
      elo    = 1400
    )
    val json = mapper.writeValueAsString(event)
    json should include("\"gameId\":\"abc-123\"")
    json should include("\"color\":\"black\"")
    json should include("\"elo\":1400")
  }

  it should "round-trip through JSON without data loss" in {
    val original = BotMoveRequestEvent("g1", "fen-string", "white", 800)
    val json     = mapper.writeValueAsString(original)
    val decoded  = mapper.readValue(json, classOf[BotMoveRequestEvent])
    decoded should be(original)
  }

  it should "include the FEN field in the serialised payload" in {
    val fen   = "8/8/8/8/8/8/PPPPPPPP/RNBQKBNR w KQ - 0 1"
    val event = BotMoveRequestEvent("g2", fen, "white", 1800)
    val json  = mapper.writeValueAsString(event)
    json should include(fen)
  }

  // ── BotResponseEvent deserialisation ───────────────────────────────────────

  "BotResponseEvent" should "deserialise a valid JSON response" in {
    val json = """{"gameId":"g3","from":"e2","to":"e4"}"""
    val resp = mapper.readValue(json, classOf[BotResponseEvent])
    resp.gameId should be("g3")
    resp.from   should be("e2")
    resp.to     should be("e4")
  }

  it should "use empty strings for missing fields (default values)" in {
    val json = """{}"""
    val resp = mapper.readValue(json, classOf[BotResponseEvent])
    resp.gameId should be("")
    resp.from   should be("")
    resp.to     should be("")
  }
