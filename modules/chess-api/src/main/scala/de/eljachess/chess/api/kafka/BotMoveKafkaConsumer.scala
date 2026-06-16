package de.eljachess.chess.api.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.eljachess.chess.api.service.GameService
import jakarta.annotation.{PostConstruct, PreDestroy}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.apache.kafka.clients.consumer.KafkaConsumer
import scala.compiletime.uninitialized
import java.util.Properties
import scala.jdk.CollectionConverters.*

/** JSON payload received from chess.bot-responses */
case class BotResponseEvent(
  gameId: String = "",
  from:   String = "",
  to:     String = ""
)

/**
 * Kafka consumer: applies bot move responses to in-flight games.
 *
 * Polls chess.bot-responses on a dedicated daemon thread.
 * For each response the corresponding game in GameService is updated
 * by calling applyBotMoveAsync(gameId, from, to).
 *
 * Disabled via env var KAFKA_ENABLED=false (used in @QuarkusTest profile
 * where no Kafka broker is available).
 */
@ApplicationScoped
class BotMoveKafkaConsumer:

  @Inject var gameService: GameService = uninitialized

  private val bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  private val enabled          = sys.env.getOrElse("KAFKA_ENABLED", "true") == "true"

  private val TOPIC = "chess.bot-responses"

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  @volatile private var stopped = false

  @PostConstruct
  def start(): Unit =
    if !enabled then return

    val props = new Properties()
    props.put("bootstrap.servers", bootstrapServers)
    props.put("key.deserializer",   "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("group.id",           "chess-api-group")
    props.put("auto.offset.reset",  "latest")

    val consumer = new KafkaConsumer[String, String](props)
    consumer.subscribe(java.util.Collections.singletonList(TOPIC))

    val poller = new Thread(
      () => {
        while !stopped do
          consumer.poll(java.time.Duration.ofMillis(100)).asScala.foreach { record =>
            val event = mapper.readValue(record.value(), classOf[BotResponseEvent])
            gameService.applyBotMoveAsync(event.gameId, event.from, event.to)
          }
        consumer.close()
      },
      "kafka-bot-response-poller"
    )
    poller.setDaemon(true)
    poller.start()

  @PreDestroy
  def shutdown(): Unit =
    stopped = true
