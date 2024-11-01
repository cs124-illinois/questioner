@file:Suppress("PackageUpdate")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    kotlin("jvm")
    application
    id("org.jmailen.kotlinter")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.7.0"
}
dependencies {
    val ktorVersion = "3.0.1"

    testJavaagent("com.beyondgrader.resource-agent:agent:2024.7.0")

    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    implementation(project(":lib"))

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.mongodb:mongodb-driver:3.12.14")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
}
tasks.withType<Test> {
    enableAssertions = true

    environment["MONGODB_TESTING"] = "mongodb://localhost:27017/testing"
    environment["QUESTIONER_QUESTION_CACHE_SIZE"] = 4
    environment["QUESTIONER_TEST_TIMEOUT_MS"] = 160
}
tasks.shadowJar {
    manifest {
        attributes["Launcher-Agent-Class"] = "com.beyondgrader.resourceagent.AgentKt"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}
application {
    mainClass.set("edu.illinois.cs.cs124.stumperd.MainKt")
}
tasks.withType<ShadowJar> {
    isZip64 = true
}
val dockerName = "cs124/stumperd"
tasks.register<Copy>("dockerCopyJar") {
    from(tasks["shadowJar"].outputs)
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Copy>("dockerCopyDockerfile") {
    from("${projectDir}/Dockerfile")
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Exec>("dockerBuild") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    environment("DOCKER_BUILDKIT", "1")
    commandLine(
        ("docker build . " +
            "-t ${dockerName}:latest " +
            "-t ${dockerName}:${project.version}").split(" ")
    )
}
tasks.register<Exec>("dockerPush") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    commandLine(
        ("docker buildx build . --platform=linux/amd64,linux/arm64/v8 " +
            "--builder multiplatform " +
            "--tag ${dockerName}:latest " +
            "--tag ${dockerName}:${project.version} --push").split(" ")
    )
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.withType<FormatTask> {
    this.source = this.source.minus(fileTree("build")).asFileTree
}
tasks.withType<LintTask> {
    this.source = this.source.minus(fileTree("build")).asFileTree
}

