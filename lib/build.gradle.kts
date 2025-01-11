@file:Suppress("PackageUpdate")

import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.7.0"
}
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    testJavaagent("com.beyondgrader.resource-agent:agent:2024.7.0")

    implementation("org.apache.commons:commons-text:1.13.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.15")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.mongodb:mongodb-driver:3.12.14")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.1.0")

    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    api("com.squareup.moshi:moshi-kotlin:1.15.2")
    api("com.beyondgrader.resource-agent:agent:2024.7.0")
    api("com.beyondgrader.resource-agent:virtualfsplugin:2024.7.0") {
        exclude(group = "com.github.cs124-illinois.jeed", module = "core")
    }
    api("org.cs124.jeed:core:2025.1.0")
    api("org.cs124:jenisol:2025.1.0")
    api("org.cs124:libcs1:2025.1.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    api("io.kotest:kotest-runner-junit5:5.9.1")
    api("com.google.truth:truth:1.4.4")

    api("io.github.cdimascio:dotenv-kotlin:6.5.0")
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
tasks.compileKotlin {
    dependsOn("createProperties")
}
task("createProperties") {
    dependsOn(tasks.processResources)
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs124.questioner.lib.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
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
