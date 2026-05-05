plugins {
    java
    id("io.gatling.gradle") version "3.15.0.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

gatling {
    // Use the bundled Gatling version shipped with the plugin
}

dependencies {
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.15.0") {
        exclude(group = "org.scala-lang")
    }
}
