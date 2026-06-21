plugins {
    id("io.quarkus")
    id("scala")
}

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

val SCALA_VERSION = "2.13.16"
val SPARK_VERSION = "3.5.3"

repositories {
    mavenCentral()
}

scala {
    scalaVersion = SCALA_VERSION
}

// JVM flags required for Spark 3.5 on Java 17+
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

dependencies {
    // Use platform (not enforcedPlatform) so Spark can manage its own transitive versions
    implementation(platform("io.quarkus.platform:quarkus-bom:${versions["QUARKUS"]!!}"))

    implementation("org.scala-lang:scala-library:$SCALA_VERSION")
    implementation("org.scala-lang:scala-reflect:$SCALA_VERSION")

    // Quarkus REST + Health + OpenAPI
    implementation("io.quarkus:quarkus-rest") {
        exclude(group = "org.scala-lang")
    }
    implementation("io.quarkus:quarkus-rest-jackson") {
        exclude(group = "org.scala-lang")
    }
    implementation("io.quarkus:quarkus-smallrye-health") {
        exclude(group = "org.scala-lang")
    }
    implementation("io.quarkus:quarkus-smallrye-openapi") {
        exclude(group = "org.scala-lang")
    }

    // Spark SQL (local mode, Scala 2.13 build)
    implementation("org.apache.spark:spark-sql_2.13:$SPARK_VERSION") {
        exclude(group = "org.scala-lang")
        // Let Quarkus BOM manage Jackson versions
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.module", module = "jackson-module-scala_2.13")
        exclude(group = "com.fasterxml.jackson.datatype", module = "jackson-datatype-jsr310")
    }

    // Jackson Scala module for case class serialization (must match Quarkus Jackson version)
    implementation("com.fasterxml.jackson.module:jackson-module-scala_2.13:2.18.3") {
        exclude(group = "org.scala-lang")
    }

    // Spark 3.5 still depends on javax.servlet (not jakarta.servlet)
    implementation("javax.servlet:javax.servlet-api:4.0.1")

    // Force log4j to a consistent version: Quarkus BOM brings log4j-core 2.23+,
    // but Spark's transitive log4j-api is older → mismatched API → NoSuchMethodError.
    // Pinning both to 2.23.1 aligns them.
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")

    // Testing — JUnit 4 runner (more reliable with Spark background threads)
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.scalatest:scalatest_2.13:${versions["SCALATEST"]!!}")
    testImplementation("org.scalatestplus:junit-4-13_2.13:3.2.19.0")
}

// Align transitive dependency versions to resolve conflicts between Spark and Quarkus:
//   log4j: Quarkus brings log4j-core 2.23+ which needs log4j-api 2.23+ (Spark brings 2.20)
//   antlr4: Quarkus (via Hibernate) brings antlr4-runtime 4.13+ (ATN format v4),
//           but Spark's SqlBaseLexer was compiled with ANTLR 4.9.3 (ATN format v3)
configurations.all {
    resolutionStrategy {
        force("org.apache.logging.log4j:log4j-api:2.23.1")
        force("org.apache.logging.log4j:log4j-core:2.23.1")
        force("org.antlr:antlr4-runtime:4.9.3")
    }
}

// For quarkusDev, pass Spark JVM args via environment:
//   JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED ..." ./gradlew :modules:analytics-service:quarkusDev

tasks.test {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
    jvmArgs(
        sparkJvmArgs + listOf(
            "-Dspark.ui.enabled=false",
            "-Dspark.sql.shuffle.partitions=1",
            "-Dspark.driver.host=127.0.0.1",
            "-Dspark.driver.bindAddress=127.0.0.1"
        )
    )
}
