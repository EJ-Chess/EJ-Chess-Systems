plugins {
    id("scala")
    id("org.scoverage") version "8.1"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

@Suppress("UNCHECKED_CAST")
val versions = rootProject.extra["VERSIONS"] as Map<String, String>

repositories {
    mavenCentral()
}

scala {
    scalaVersion = versions["SCALA3"]!!
}

scoverage {
    scoverageVersion.set(versions["SCOVERAGE"]!!)
    excludedFiles.addAll(".*ChessGUI.*", ".*ChessApp.*")
}

application {
    mainClass.set("de.eljachess.chess.Main")
}

tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-encoding", "UTF-8")
}

tasks.named<JavaExec>("run") {
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "--add-modules=javafx.controls,javafx.graphics"
    )
    standardInput = System.`in`
}

dependencies {

    implementation("org.scala-lang:scala3-compiler_3") {
        version {
            strictly(versions["SCALA3"]!!)
        }
    }
    implementation("org.scala-lang:scala3-library_3") {
        version {
            strictly(versions["SCALA3"]!!)
        }
    }

    implementation("org.scalafx:scalafx_3:${versions["SCALAFX"]!!}")
    listOf("javafx-base", "javafx-controls", "javafx-graphics").forEach { module ->
        implementation("org.openjfx:$module:${versions["JAVAFX"]!!}:win")
    }

    testImplementation(platform("org.junit:junit-bom:${versions["JUNIT_BOM"]!!}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
    testImplementation("co.helmethair:scalatest-junit-runner:${versions["SCALATEST_JUNIT"]!!}")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage {
    dependsOn(tasks.test)
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Run FEN micro-benchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.eljachess.chess.model.fenBenchmark")
    jvmArgs("-Xss4m", "-Xmx512m")
}
