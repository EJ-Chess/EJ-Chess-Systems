plugins {
    id("scala")
    application
}

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

repositories {
    mavenCentral()
}

val SCALA_VERSION = "2.13.16"
val SPARK_VERSION = "3.5.3"

scala {
    scalaVersion = SCALA_VERSION
}

application {
    mainClass.set("de.eljachess.spark.ChessFileAnalytics")
}

dependencies {
    // Scala 2.13 runtime — Spark 3.5.x ships with 2.13 builds
    implementation("org.scala-lang:scala-library:$SCALA_VERSION")

    // Spark SQL (includes spark-core) — Scala 2.13 variant
    implementation("org.apache.spark:spark-sql_2.13:$SPARK_VERSION")

    // Structured Streaming + Kafka source connector
    implementation("org.apache.spark:spark-sql-kafka-0-10_2.13:$SPARK_VERSION")

    // Test — use JUnit 4 runner (more reliable with Spark's background threads)
    testImplementation("org.scalatest:scalatest_2.13:${versions["SCALATEST"]!!}")
    testImplementation("org.scalatestplus:junit-4-13_2.13:3.2.19.0")
}

// ── Run targets ──────────────────────────────────────────────────────────────

// JVM args required for Spark 3.5 on Java 17+ (module access)
val sparkJvmArgs = listOf(
    "-Dhadoop.home.dir=${System.getenv("HADOOP_HOME") ?: System.getProperty("java.io.tmpdir")}",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
)

tasks.named<JavaExec>("run") {
    description = "Run chess file-based analytics (default)"
    jvmArgs(sparkJvmArgs + listOf("-Dspark.sql.shuffle.partitions=4"))
}

tasks.register<JavaExec>("runKafkaStream") {
    group = "application"
    description = "Run chess Kafka Structured Streaming analytics"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.eljachess.spark.ChessKafkaStream")
    jvmArgs(sparkJvmArgs)
    environment(
        "KAFKA_BOOTSTRAP_SERVERS",
        System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"
    )
}

tasks.register<JavaExec>("runLichess") {
    group = "application"
    description = "Run chess analytics on a Lichess PGN file (--args=\"/path/to/file.pgn\")"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.eljachess.spark.LichessFileAnalytics")
    jvmArgs(sparkJvmArgs + listOf("-Dspark.sql.shuffle.partitions=4"))
}

// ── Test ─────────────────────────────────────────────────────────────────────

tasks.test {
    // JUnit 4 avoids the Gradle 9 / Spark thread-tracking conflict that
    // occurs with the JUnit 5 Platform engine.
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
    jvmArgs(sparkJvmArgs + listOf(
        "-Dspark.ui.enabled=false",
        "-Dspark.sql.shuffle.partitions=1",
        "-Dspark.driver.host=127.0.0.1",
        "-Dspark.driver.bindAddress=127.0.0.1"
    ))
}
