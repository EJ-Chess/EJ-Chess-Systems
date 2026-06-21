package de.eljachess.analytics

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.Row
import org.eclipse.microprofile.config.inject.ConfigProperty

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

/** Mirrors the AnalyticsGameRow DTO returned by game-service /games/analytics-export. */
private case class GameServiceRow(
  gameId:      String = "",
  playerName:  String = "",
  playerColor: String = "",
  winner:      String = "",
  botElo:      Int    = 0,
  moveCount:   Int    = 0
)

@ApplicationScoped
class AnalyticsService {

  @ConfigProperty(name = "game.service.url", defaultValue = "http://localhost:8080")
  var gameServiceUrl: String = _

  @ConfigProperty(name = "lichess.pgn.path", defaultValue = "/data/lichess.pgn")
  var lichessPgnPath: String = _

  private val state: AtomicReference[AnalyticsResult] =
    new AtomicReference(AnalyticsResult.idle)

  @volatile private var running = false

  private lazy val spark: SparkSession = SparkSession.builder()
    .appName("EJa Chess Analytics")
    .config("spark.master", "local[*]")
    .config("spark.ui.enabled", "false")
    .config("spark.sql.shuffle.partitions", "4")
    .getOrCreate()

  private val mapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private val gameSchema = StructType(Seq(
    StructField("gameId",      StringType,  nullable = false),
    StructField("playerName",  StringType,  nullable = false),
    StructField("playerColor", StringType,  nullable = false),
    StructField("winner",      StringType,  nullable = false),
    StructField("botElo",      IntegerType, nullable = false),
    StructField("moveCount",   IntegerType, nullable = false)
  ))

  def getResult: AnalyticsResult = state.get()

  /** Triggers the Spark job in the background. Ignored if already running. */
  def runAsync(source: String = "local"): Unit = synchronized {
    if (running) return
    running = true
    state.set(AnalyticsResult("RUNNING", None, Nil, Nil, Nil, "", source))

    val t = new Thread(() => {
      try {
        val result = computeAnalytics(source)
        state.set(result)
      } catch {
        case e: Exception =>
          state.set(AnalyticsResult("ERROR", Some(Instant.now().toString), Nil, Nil, Nil, e.getMessage, source))
      } finally {
        running = false
      }
    }, "analytics-spark-runner")
    t.setDaemon(true)
    t.start()
  }

  /** Runs the Spark aggregations. Exposed as package-private for tests. */
  private[analytics] def computeAnalytics(source: String = "local"): AnalyticsResult = {
    val df = if (source == "lichess") {
      val path = Option(lichessPgnPath).getOrElse("/data/lichess.pgn")
      val rows = parsePgn(path)
      if (rows.isEmpty) throw new IllegalStateException(s"No games parsed from PGN at $path")
      val javaRows = spark.sparkContext.parallelize(
        rows.map(r => Row(r.gameId, r.playerName, r.playerColor, r.winner, r.botElo, r.moveCount))
      )
      spark.createDataFrame(javaRows, gameSchema)
    } else {
      // local: try game-service first, fall back to bundled CSV
      val liveRows = fetchFromGameService()
      if (liveRows.nonEmpty) {
        val javaRows = spark.sparkContext.parallelize(
          liveRows.map(r => Row(r.gameId, r.playerName, r.playerColor, r.winner, r.botElo, r.moveCount))
        )
        spark.createDataFrame(javaRows, gameSchema)
      } else {
        spark.read
          .option("header", "true")
          .schema(gameSchema)
          .csv(csvFromClasspath())
      }
    }

    val cached = df.cache()

    val victories = cached
      .filter(col("playerColor") === col("winner"))
      .groupBy("playerName")
      .agg(count("*").as("victories"))
      .orderBy(col("victories").desc)
      .collect()
      .map(r => PlayerVictory(r.getString(0), r.getLong(1)))
      .toList

    val colorWins = cached
      .filter(col("winner") =!= lit("draw"))
      .groupBy("winner")
      .agg(count("*").as("total_wins"))
      .orderBy(col("total_wins").desc)
      .collect()
      .map(r => ColorWin(r.getString(0), r.getLong(1)))
      .toList

    val avgElo = cached
      .filter(col("playerColor") === col("winner"))
      .filter(col("botElo") > 0)
      .groupBy("playerName")
      .agg(avg(col("botElo")).as("avg_bot_elo_beaten"))
      .orderBy(col("avg_bot_elo_beaten").desc)
      .collect()
      .map(r => PlayerElo(r.getString(0), r.getDouble(1)))
      .toList

    val best = victories.headOption.map(_.player).getOrElse("No games played")

    cached.unpersist()

    AnalyticsResult(
      status              = "DONE",
      runAt               = Some(Instant.now().toString),
      victoriesPerPlayer  = victories,
      winsPerColor        = colorWins,
      avgEloBeatPerPlayer = avgElo,
      bestPlayer          = best,
      dataSource          = source
    )
  }

  /**
   * Parses a Lichess PGN export file.
   * Each game produces two rows (one per player) so the same Spark
   * aggregations work: victories per player, wins per color, avg opponent ELO beaten.
   */
  private[analytics] def parsePgn(path: String): List[GameServiceRow] = {
    val tagRe     = """\[(\w+)\s+"([^"]*)"\]""".r
    val moveNumRe = """(\d+)\.""".r
    val buf       = mutable.ListBuffer.empty[GameServiceRow]
    val src       = scala.io.Source.fromFile(path, "UTF-8")

    try {
      var tags    = mutable.Map.empty[String, String]
      val moveBuf = new StringBuilder
      var inMoves = false

      def flush(): Unit = if (tags.nonEmpty) {
        val white  = tags.getOrElse("White", "")
        val black  = tags.getOrElse("Black", "")
        val res    = tags.getOrElse("Result", "*")
        val wElo   = tags.get("WhiteElo").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
        val bElo   = tags.get("BlackElo").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
        val gameId = tags.getOrElse("Site", s"pgn-${tags.##}")
        val winner = res match {
          case "1-0"     => "white"
          case "0-1"     => "black"
          case "1/2-1/2" => "draw"
          case _         => ""
        }
        if (winner.nonEmpty && white.nonEmpty && black.nonEmpty) {
          val mc = moveNumRe.findAllMatchIn(moveBuf).map(_.group(1).toInt).toList.lastOption.getOrElse(0)
          buf += GameServiceRow(s"$gameId-w", white, "white", winner, bElo, mc)
          buf += GameServiceRow(s"$gameId-b", black, "black", winner, wElo, mc)
        }
        tags = mutable.Map.empty
        moveBuf.clear()
        inMoves = false
      }

      for (line <- src.getLines()) {
        val t = line.trim
        if (t.startsWith("[")) {
          if (inMoves) flush()
          tagRe.findFirstMatchIn(t).foreach(m => tags(m.group(1)) = m.group(2))
        } else if (t.nonEmpty) {
          inMoves = true
          moveBuf.append(t).append(' ')
        }
      }
      flush()
    } finally src.close()

    buf.toList
  }

  private def fetchFromGameService(): List[GameServiceRow] =
    try {
      val url = Option(gameServiceUrl).getOrElse("http://localhost:8080")
      val client  = java.net.http.HttpClient.newHttpClient()
      val request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(s"$url/games/analytics-export"))
        .timeout(java.time.Duration.ofSeconds(10))
        .GET()
        .build()
      val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() == 200) {
        val rows = mapper.readValue(response.body(), classOf[Array[GameServiceRow]])
        rows.toList
      } else Nil
    } catch {
      case _: Exception => Nil
    }

  private def csvFromClasspath(): String = {
    val url = getClass.getClassLoader.getResource("chess_games.csv")
    if (url == null) throw new IllegalStateException("chess_games.csv not found on classpath")
    if (url.getProtocol == "file") {
      java.nio.file.Paths.get(url.toURI).toString
    } else {
      val tmp = java.io.File.createTempFile("chess_games", ".csv")
      tmp.deleteOnExit()
      val in = url.openStream()
      try {
        java.nio.file.Files.copy(in, tmp.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      } finally {
        in.close()
      }
      tmp.getAbsolutePath
    }
  }

  @PreDestroy
  def onShutdown(): Unit = {
    if (!spark.sparkContext.isStopped) spark.stop()
  }
}
