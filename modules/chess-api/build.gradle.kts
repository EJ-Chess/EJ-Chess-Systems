plugins {
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
    // Scala 3 runtime
    implementation("org.scala-lang:scala3-library_3") {
        version { strictly(versions["SCALA3"]!!) }
    }

    // Core: import chess domain from core module
    implementation(project(":core"))

    // Quarkus REST (quarkus-rest / quarkus-rest-jackson only available from 3.9.0+;
    //              for Quarkus 3.8.x use the resteasy-reactive equivalents)
    implementation("io.quarkus:quarkus-resteasy-reactive:${versions["QUARKUS"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson:${versions["QUARKUS"]!!}") {
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
    // GameControllerSpec uses @QuarkusTest which requires the io.quarkus Gradle plugin.
    // That plugin is incompatible with Gradle 9.2.0 (see docs/unresolved.md).
    // Exclude it from default test run until Quarkus is upgraded to 3.25+.
    exclude("**/controller/**")
}
