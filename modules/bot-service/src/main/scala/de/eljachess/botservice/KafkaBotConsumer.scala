package de.eljachess.botservice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.eljachess.chess.model.Color
import jakarta.annotation.{PostConstruct, PreDestroy}
import jakarta.enterprise.context.ApplicationScoped
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import java.util.Properties
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

/** Kafka event received on chess.move-requests */
case class BotMoveEvent(
  gameId: String = "",
  fen:    String = "",
  color:  String = "",
  elo:    Int    = 0
)

/** Kafka event produced to chess.bot-responses */
case class BotResponseEvent(
  gameId: String = "",
  from:   String = "",
  to:     String = ""
)

/**
 * Connects Kafka → Pekko Stream → Kafka for bot move computation.
 *
 * Flow:
 *   chess.move-requests (Kafka)
 *     → dedicated poller thread
 *       → Source.queue (Pekko)
 *         → mapAsync(8): BotEngine.bestMove
 *           → KafkaProducer → chess.bot-responses (Kafka)
 *
 * Replaces the synchronous HTTP call from chess-api → bot-service with an
 * asynchronous, event-driven channel.  The Pekko stream provides bounded
 * concurrency and backpressure — the same approach as BotStreamProcessor.
 *
 * Disabled via env var KAFKA_ENABLED=false (used in tests).
 */
@ApplicationScoped
class KafkaBotConsumer:

  private val bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  private val enabled          = sys.env.getOrElse("KAFKA_ENABLED", "true") == "true"

  private val REQUEST_TOPIC  = "chess.move-requests"
  private val RESPONSE_TOPIC = "chess.bot-responses"

  private given system: ActorSystem  = ActorSystem("kafka-bot-stream")
  private given ec: ExecutionContext = system.dispatcher

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  @volatile private var stopped = false

  /** Called by CDI on bean activation. Starts the poller thread + Pekko stream. */
  @PostConstruct
  def start(): Unit =
    if !enabled then return

    val consumerProps = new Properties()
    consumerProps.put("bootstrap.servers", bootstrapServers)
    consumerProps.put("key.deserializer",   "org.apache.kafka.common.serialization.StringDeserializer")
    consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    consumerProps.put("group.id",           "bot-service-group")
    consumerProps.put("auto.offset.reset",  "latest")

    val producerProps = new Properties()
    producerProps.put("bootstrap.servers", bootstrapServers)
    producerProps.put("key.serializer",   "org.apache.kafka.common.serialization.StringSerializer")
    producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producerProps.put("acks",    "1")
    producerProps.put("retries", "2")

    val kafkaConsumer = new KafkaConsumer[String, String](consumerProps)
    val kafkaProducer = new KafkaProducer[String, String](producerProps)

    kafkaConsumer.subscribe(java.util.Collections.singletonList(REQUEST_TOPIC))

    // Pekko stream: bounded queue → parallel bot computation → fire-and-forget Kafka send
    val (queue, _) =
      Source
        .queue[ConsumerRecord[String, String]](bufferSize = 1000, OverflowStrategy.dropNew)
        .mapAsync(parallelism = 8) { record =>
          Future {
            processRecord(record, kafkaProducer)
          }
        }
        .toMat(Sink.ignore)(Keep.both)
        .run()

    // Dedicated poller: KafkaConsumer is not thread-safe, runs on one thread only
    // Kafka Records in die Pekko Queue
    val poller = new Thread(
      () => {
        while !stopped do
          kafkaConsumer.poll(java.time.Duration.ofMillis(100)).asScala.foreach { record =>
            queue.offer(record)
          }
        kafkaConsumer.close()
        kafkaProducer.close()
      },
      "kafka-bot-poller"
    )
    poller.setDaemon(true)
    poller.start()

  /**
   * Process one Kafka record: deserialise → BotEngine → publish response.
   * Extracted for unit-testability (no Kafka broker required).
   */
  private[botservice] def processRecord(
    record:   ConsumerRecord[String, String],
    producer: KafkaProducer[String, String]
  ): Unit =
    val event = mapper.readValue(record.value(), classOf[BotMoveEvent])
    processMoveEvent(event, producer)

  private[botservice] def processMoveEvent(
    event:    BotMoveEvent,
    producer: KafkaProducer[String, String]
  ): Unit =
    val color = if event.color.toLowerCase == "black" then Color.Black else Color.White
    BotEngine.bestMove(event.fen, color, event.elo).foreach { case (from, to) =>
      val response = mapper.writeValueAsString(BotResponseEvent(event.gameId, from, to))
      producer.send(new ProducerRecord(RESPONSE_TOPIC, event.gameId, response)) // Kafka
    }

  @PreDestroy
  def shutdown(): Unit =
    stopped = true
    system.terminate()
