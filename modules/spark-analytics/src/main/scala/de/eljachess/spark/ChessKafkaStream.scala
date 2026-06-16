package de.eljachess.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

/**
 * Step 2 – Kafka Structured Streaming analytics.
 *
 * Connects to the chess.move-requests Kafka topic and continuously aggregates
 * the number of bot-move requests per game and color.  Each output batch shows
 * the running total since the stream started.
 *
 * Kafka message format (JSON):
 *   { "gameId": "...", "fen": "...", "color": "white|black", "elo": 1500 }
 *
 * Usage:
 *   ./gradlew :modules:spark-analytics:runKafkaStream
 *   KAFKA_BOOTSTRAP_SERVERS=broker:9092 ./gradlew :modules:spark-analytics:runKafkaStream
 */
object ChessKafkaStream {

  private val MoveRequestSchema: StructType = StructType(Seq(
    StructField("gameId", StringType,  nullable = true),
    StructField("fen",    StringType,  nullable = true),
    StructField("color",  StringType,  nullable = true),
    StructField("elo",    IntegerType, nullable = true)
  ))

  def main(args: Array[String]): Unit = {
    val bootstrapServers =
      sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")

    val spark = SparkSession.builder()
      .appName("Chess Kafka Stream Analytics")
      .config("spark.master", "local[*]")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    // ── Source: chess.move-requests ──────────────────────────────────────────
    val raw = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrapServers)
      .option("subscribe", "chess.move-requests")
      .option("startingOffsets", "latest")
      .load()

    // ── Transformation: parse JSON and aggregate ──────────────────────────────
    val parsed = raw
      .select(from_json(col("value").cast("string"), MoveRequestSchema).as("data"))
      .select("data.*")

    // Running count of move-requests per game/color combination
    val movesPerGame = parsed
      .groupBy(col("gameId"), col("color"))
      .agg(
        count("*").as("move_requests"),
        avg(col("elo")).as("avg_elo")
      )

    // ── Sink: console ─────────────────────────────────────────────────────────
    println()
    println("══════════════════════════════════════════════")
    println("  Chess Kafka Stream Analytics")
    println(s"  Broker : $bootstrapServers")
    println(s"  Topic  : chess.move-requests")
    println("══════════════════════════════════════════════")
    println("  Press Ctrl+C to stop.\n")

    movesPerGame.writeStream
      .outputMode("complete")
      .format("console")
      .option("truncate", "false")
      .start()
      .awaitTermination()
  }
}
