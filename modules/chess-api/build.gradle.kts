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
    testImplementation("io.quarkus:quarkus-test-client:${versions["QUARKUS"]!!}")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
}

tasks.test {
    useJUnitPlatform()
}
