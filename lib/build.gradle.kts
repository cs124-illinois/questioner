@file:Suppress("PackageUpdate", "SpellCheckingInspection")

import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
    signing
    id("com.ryandens.javaagent-test") version "0.10.0"
    id("com.autonomousapps.testkit")
}
dependencies {
    testJavaagent("com.beyondgrader.resource-agent:agent:2026.1.2")

    implementation("org.apache.commons:commons-text:1.15.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.16")
    implementation("org.ow2.asm:asm:9.9.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.10")
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    api("com.beyondgrader.resource-agent:agent:2026.1.2")
    api("com.beyondgrader.resource-agent:virtualfsplugin:2026.1.2") {
        exclude(group = "com.github.cs124-illinois.jeed", module = "core")
    }
    api("org.cs124.jeed:core:2026.2.0")
    api("org.cs124:jenisol:2026.2.0")
    api("org.cs124:libcs1:2026.2.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.21.1")

    testImplementation("io.kotest:kotest-runner-junit5:6.1.4")
    api("org.junit.jupiter:junit-jupiter-api:6.0.3")
    api("org.junit.platform:junit-platform-engine:6.0.3")
    api("com.google.truth:truth:1.4.5")

    api("io.github.cdimascio:dotenv-kotlin:6.5.1")

    api("org.slf4j:slf4j-api:2.0.17")
    api("ch.qos.logback:logback-classic:1.5.32")
    api("io.github.microutils:kotlin-logging:3.0.5")
}
tasks {
    val sourcesJar by registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
    artifacts {
        add("archives", sourcesJar)
    }
}
tasks.register("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs124.questioner.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
tasks.compileKotlin {
    dependsOn("createProperties")
}
tasks.test {
    dependsOn(":plugin:functionalTest")
}
tasks.processResources {
    dependsOn("createProperties")
}
java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
publishing {
    publications {
        create<MavenPublication>("questioner") {
            artifactId = "lib"
            from(components["java"])
            pom {
                name = "questioner"
                description = "Question authoring library for CS 124."
                url = "https://cs124.org"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit/"
                    }
                }
                developers {
                    developer {
                        id = "gchallen"
                        name = "Geoffrey Challen"
                        email = "challen@illinois.edu"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/cs124-illinois/questioner.git"
                    developerConnection = "scm:git:https://github.com/cs124-illinois/questioner.git"
                    url = "https://github.com/cs124-illinois/questioner"
                }
            }
        }
    }
}
signing {
    sign(publishing.publications["questioner"])
}
