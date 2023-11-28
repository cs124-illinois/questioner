import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.5.1"
}
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    testJavaagent("com.beyondgrader.resource-agent:agent:2023.9.0")

    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("org.apache.commons:commons-text:1.11.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.mongodb:mongodb-driver:3.12.14")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.21")

    api("com.beyondgrader.resource-agent:agent:2023.9.0")
    api("com.beyondgrader.resource-agent:virtualfsplugin:2023.9.0") {
        exclude(group = "com.github.cs124-illinois.jeed", module = "core")
    }
    api("org.cs124.jeed:core:2023.11.5")
    api("org.cs124:jenisol:2023.11.5")
    api("org.cs124:libcs1:2023.11.1")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.0")

    api("io.kotest:kotest-runner-junit5:5.8.0")
    api("com.google.truth:truth:1.1.5")
}
tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
    artifacts {
        add("archives", sourcesJar)
    }
}
tasks.withType(Test::class.java) {
    environment["JEED_USE_CACHE"] = "true"

    jvmArgs(
        "-ea", "--enable-preview", "-Dfile.encoding=UTF-8",
        "-Xms512m", "-Xmx1G", "-Xss256k", "-XX:+UseZGC", "-XX:ZCollectionInterval=8",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-exports", "java.management/sun.management=ALL-UNNAMED"
    )
}
kotlin {
    kotlinDaemonJvmArgs = listOf("-Dfile.encoding=UTF-8")
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
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
publishing {
    publications {
        create<MavenPublication>("questioner") {
            artifactId = "questioner"
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
    setRequired {
        gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }
    sign(publishing.publications["questioner"])
}
