import java.io.File
import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    `maven-publish`
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.5.0"
}
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    testJavaagent("com.beyondgrader.resource-agent:agent:2023.9.0")

    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("org.ow2.asm:asm:9.6")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.mongodb:mongodb-driver:3.12.14")

    api("com.beyondgrader.resource-agent:agent:2023.9.0")
    api("com.github.cs124-illinois.jeed:core:2023.10.4")
    api("com.github.cs124-illinois:jenisol:2023.10.1")
    api("com.github.cs124-illinois:libcs1:2023.10.0")

    api("io.kotest:kotest-runner-junit5:5.7.2")
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
publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
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
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
