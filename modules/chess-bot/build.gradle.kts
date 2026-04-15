plugins {
    id("scala")
    id("org.scoverage") version "8.1"
}

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
}

dependencies {
    implementation(project(":core"))

    implementation("org.scala-lang:scala3-library_3") {
        version { strictly(versions["SCALA3"]!!) }
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
