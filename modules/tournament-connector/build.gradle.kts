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

    // Jackson Scala module (Option, List, Map support)
    implementation("com.fasterxml.jackson.module:jackson-module-scala_3:2.18.3") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }

    // Quarkus REST (server + Jackson provider)
    implementation("io.quarkus:quarkus-rest:${versions["QUARKUS"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    implementation("io.quarkus:quarkus-rest-jackson:${versions["QUARKUS"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }

    // Health + OpenAPI
    implementation("io.quarkus:quarkus-smallrye-health:${versions["QUARKUS"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    implementation("io.quarkus:quarkus-smallrye-openapi:${versions["QUARKUS"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }

    // Testing
    testImplementation("io.quarkus:quarkus-junit5:${versions["QUARKUS"]!!}")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    testImplementation("co.helmethair:scalatest-junit-runner:${versions["SCALATEST_JUNIT"]!!}")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest", "junit-jupiter")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
