plugins {
    kotlin("jvm")
    `maven-publish`
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.4.2"
}
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")

    testJavaagent("com.beyondgrader.resource-agent:agent:2022.9.3")

    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("org.ow2.asm:asm:9.4")

    api("com.beyondgrader.resource-agent:agent:2022.9.3")
    api("com.github.cs124-illinois.jeed:core:2023.2.0")
    api("com.github.cs124-illinois:jenisol:2023.2.0")
    api("io.kotest:kotest-runner-junit5:5.5.4")
    api("com.google.truth:truth:1.1.3")
    api("com.github.cs124-illinois:libcs1:2023.1.1")
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
