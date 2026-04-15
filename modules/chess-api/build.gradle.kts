plugins {
    id("io.quarkus")
    kotlin("jvm")
    scala
}

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

repositories {
    mavenCentral()
}

scala {
    scalaVersion = versions["SCALA3"]!!
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${versions["QUARKUS"]!!}"))

    // Scala 3 runtime
    implementation("org.scala-lang:scala3-library_3") {
        version { strictly(versions["SCALA3"]!!) }
    }

    // Core: import chess domain from core module
    implementation(project(":core"))

    // Bot: game setup with configurable bot difficulty
    implementation(project(":modules:chess-bot"))

    // JavaFX / ScalaFX (for local GUI startup)
    implementation("org.scalafx:scalafx_3:${versions["SCALAFX"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    listOf("javafx-base", "javafx-controls", "javafx-graphics").forEach { module ->
        implementation("org.openjfx:$module:${versions["JAVAFX"]!!}:win")
    }

    // Jackson Scala module (Option, Seq, Map support)
    implementation("com.fasterxml.jackson.module:jackson-module-scala_3:2.18.3") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }

    // Quarkus REST
    implementation("io.quarkus:quarkus-rest:${versions["QUARKUS"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    implementation("io.quarkus:quarkus-rest-jackson:${versions["QUARKUS"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }

    // Testing
    testImplementation("io.quarkus:quarkus-junit5:${versions["QUARKUS"]!!}")
    testImplementation("io.rest-assured:rest-assured:5.4.0") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
    testImplementation("co.helmethair:scalatest-junit-runner:${versions["SCALATEST_JUNIT"]!!}")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest", "junit-jupiter")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    // @QuarkusTest classes must run via ./gradlew :modules:chess-api:quarkusTest
    // Running them through the standard test task crashes the worker process.
    exclude("**/controller/**")
}
