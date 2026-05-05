plugins {
    scala
    id("me.champeau.jmh") version "0.7.3"
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
    implementation(project(":core"))
    implementation("org.scala-lang:scala3-library_3:${versions["SCALA3"]!!}") {
        version { strictly(versions["SCALA3"]!!) }
    }

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
    jmhVersion = "1.37"
    warmupIterations = 3
    iterations = 5
    fork = 1
    timeOnIteration = "1s"
    warmup = "1s"
    benchmarkMode = listOf("avgt")
    timeUnit = "us"
    resultFormat = "JSON"
    resultsFile = project.file("${project.buildDir}/results/jmh/results.json")
    // No includes filter — runs all benchmarks found in the jmh source set
}

// Make the jmh source set aware of Scala sources in src/jmh/scala
sourceSets.named("jmh") {
    scala.setSrcDirs(listOf("src/jmh/scala"))
}
