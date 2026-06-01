plugins {
    java
    scala
    id("io.gatling.gradle") version "3.15.0.1"
}

repositories {
    mavenCentral()
}

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

scala {
    scalaVersion = versions["SCALA3"]!!
}

sourceSets.named("main") {
    scala.setSrcDirs(listOf("src/main/scala"))
}

gatling {
    // Use the bundled Gatling version shipped with the plugin
}

dependencies {
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.15.0") {
        exclude(group = "org.scala-lang")
    }

    // fs2 + cats-effect for Scala stream load generator
    implementation("org.scala-lang:scala3-library_3") {
        version { strictly(versions["SCALA3"]!!) }
    }
    implementation("co.fs2:fs2-core_3:3.11.0") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    implementation("org.typelevel:cats-effect_3:3.5.7") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:${versions["JUNIT_BOM"]!!}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${versions["JUNIT_BOM"]!!}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.0")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}") {
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    testImplementation("co.helmethair:scalatest-junit-runner:${versions["SCALATEST_JUNIT"]!!}")
}

tasks.register<JavaExec>("runChessLoad") {
    group = "load"
    description = "Run fs2 stream load generator against chess-api"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.eljachess.perf.ChessStreamLoad")
    jvmArgs("-Xmx512m")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
