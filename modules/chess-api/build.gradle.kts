plugins {
    kotlin("jvm")
    scala
}

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

repositories {
    mavenCentral()
}

dependencies {
    // Core: import chess domain from core module
    implementation(project(":core"))

    // Quarkus REST
    implementation("io.quarkus:quarkus-rest:${versions["QUARKUS"]!!}")
    implementation("io.quarkus:quarkus-rest-jackson:${versions["QUARKUS"]!!}")

    // Testing
    testImplementation("io.quarkus:quarkus-junit5:${versions["QUARKUS"]!!}")
    testImplementation("io.quarkus:quarkus-test-client:${versions["QUARKUS"]!!}")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
}

tasks.test {
    useJUnitPlatform()
}
