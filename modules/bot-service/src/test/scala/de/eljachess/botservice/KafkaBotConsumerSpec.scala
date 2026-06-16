package de.eljachess.botservice

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * Unit tests for KafkaBotConsumer's move-processing logic.
 *
 * No live Kafka broker required — we test processMoveEvent directly,
 * using a stub KafkaProducer that captures sent records.
 */
class KafkaBotConsumerSpec extends AnyFlatSpec with Matchers:

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  /** Minimal stub: records the last ProducerRecord.send() call. */
  private class CapturingProducer extends KafkaProducer[String, String](stubProps()):
    @volatile var lastRecord: Option[ProducerRecord[String, String]] = None
    override def send(record: ProducerRecord[String, String]) =
      lastRecord = Some(record)
      null // return value unused in our production code

  private def stubProps() =
    val p = new java.util.Properties()
    // Deliberately invalid — no broker; producer is never actually connected
    p.put("bootstrap.servers",  "localhost:19999")
    p.put("key.serializer",     "org.apache.kafka.common.serialization.StringSerializer")
    p.put("value.serializer",   "org.apache.kafka.common.serialization.StringSerializer")
    p.put("max.block.ms",       "1")   // fail-fast if send() tries to connect
    p

  private def makeRecord(gameId: String, fen: String, color: String, elo: Int): ConsumerRecord[String, String] =
    val value = mapper.writeValueAsString(BotMoveEvent(gameId, fen, color, elo))
    new ConsumerRecord("chess.move-requests", 0, 0L, gameId, value)

  // ── processMoveEvent ────────────────────────────────────────────────────────

  "KafkaBotConsumer.processMoveEvent" should "produce a response for a valid position (white)" in {
    val consumer = new KafkaBotConsumer()
    val producer = new CapturingProducer()
    val event = BotMoveEvent(
      gameId = "game-1",
      fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      color  = "white",
      elo    = 800
    )
    consumer.processMoveEvent(event, producer)
    producer.lastRecord should not be empty
    val rec = producer.lastRecord.get
    rec.key() should be("game-1")
    val resp = mapper.readValue(rec.value(), classOf[BotResponseEvent])
    resp.gameId should be("game-1")
    resp.from should have length 2
    resp.to   should have length 2
  }

  it should "produce a response for a valid position (black)" in {
    val consumer = new KafkaBotConsumer()
    val producer = new CapturingProducer()
    val event = BotMoveEvent(
      gameId = "game-2",
      fen    = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
      color  = "black",
      elo    = 1400
    )
    consumer.processMoveEvent(event, producer)
    producer.lastRecord should not be empty
    val resp = mapper.readValue(producer.lastRecord.get.value(), classOf[BotResponseEvent])
    resp.gameId should be("game-2")
  }

  it should "not produce a response when there are no legal moves" in {
    // Stalemate: Black king on a8, white queen on b6, white king on c6 — black to move, no legal moves
    val consumer = new KafkaBotConsumer()
    val producer = new CapturingProducer()
    val event = BotMoveEvent(
      gameId = "game-3",
      fen    = "k7/8/1QK5/8/8/8/8/8 b - - 0 1",
      color  = "black",
      elo    = 1400
    )
    consumer.processMoveEvent(event, producer)
    producer.lastRecord should be(empty)
  }

  // ── processRecord (JSON parsing) ────────────────────────────────────────────

  "KafkaBotConsumer.processRecord" should "deserialise a ConsumerRecord and produce a response" in {
    val consumer = new KafkaBotConsumer()
    val producer = new CapturingProducer()
    val record   = makeRecord(
      gameId = "game-4",
      fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      color  = "white",
      elo    = 1800
    )
    consumer.processRecord(record, producer)
    producer.lastRecord should not be empty
    val resp = mapper.readValue(producer.lastRecord.get.value(), classOf[BotResponseEvent])
    resp.gameId should be("game-4")
  }

  it should "send response to chess.bot-responses topic" in {
    val consumer = new KafkaBotConsumer()
    val producer = new CapturingProducer()
    val record   = makeRecord("game-5", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "white", 800)
    consumer.processRecord(record, producer)
    producer.lastRecord.map(_.topic()) should contain("chess.bot-responses")
  }
