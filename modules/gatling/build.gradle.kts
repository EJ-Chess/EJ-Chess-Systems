plugins {
    java
    id("io.gatling.gradle") version "3.13.5.2"
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
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.13.5") {
        exclude(group = "org.scala-lang")
    }
}
