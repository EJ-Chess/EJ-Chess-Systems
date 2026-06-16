package de.eljachess.chess.api.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import jakarta.enterprise.context.ApplicationScoped
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.util.Properties

/** JSON payload sent to chess.move-requests */
case class BotMoveRequestEvent(
  gameId: String,
  fen:    String,
  color:  String,
  elo:    Int
)

/**
 * Kafka producer: publishes bot move requests to chess.move-requests.
 *
 * chess-api calls publishMoveRequest when it is the bot's turn.
 * bot-service consumes the event, computes the move, and replies on
 * chess.bot-responses.  BotMoveKafkaConsumer then applies the response.
 *
 * The KafkaProducer is lazy — no connection is opened until the first
 * publish call, so the bean is safe to inject in tests where Kafka is absent.
 */
@ApplicationScoped
class BotMoveKafkaProducer:

  private val bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  private val TOPIC = "chess.move-requests"

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  private lazy val producer: KafkaProducer[String, String] =
    val props = new Properties()
    props.put("bootstrap.servers", bootstrapServers)
    props.put("key.serializer",   "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("acks",    "0")  // fire-and-forget; game continues even if bot is down
    props.put("retries", "0")
    new KafkaProducer[String, String](props)

  /**
   * Publish a bot move request.  Non-blocking — Kafka send is async.
   * If the broker is unreachable the failure is logged internally by
   * kafka-clients but does not propagate to the caller.
   */
  def publishMoveRequest(gameId: String, fen: String, color: String, elo: Int): Unit =
    val event = BotMoveRequestEvent(gameId, fen, color, elo)
    val json  = mapper.writeValueAsString(event)
    producer.send(new ProducerRecord(TOPIC, gameId, json))
