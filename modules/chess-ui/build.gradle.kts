plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version.set("20.18.0")
    download.set(true)
    workDir.set(file("${project.projectDir}/.gradle/nodejs"))
    npmWorkDir.set(file("${project.projectDir}/.gradle/npm"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
    inputs.files(fileTree("src"))
    inputs.file("package.json")
    inputs.file("vite.config.ts")
    inputs.file("tsconfig.json")
    inputs.file("index.html")
    outputs.dir("dist")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmTest") {
    dependsOn("npmInstall")
    args.set(listOf("run", "test"))
    inputs.files(fileTree("src"))
    inputs.file("package.json")
    inputs.file("vite.config.ts")
}

// base plugin provides the build lifecycle task (assemble + check)
tasks.named("assemble") {
    dependsOn("npmBuild")
}

tasks.named("check") {
    dependsOn("npmTest")
}
