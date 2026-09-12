import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.util.zip.ZipInputStream

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("org.jmailen.kotlinter")
    id("com.gradleup.shadow") version "9.6.1"
    id("com.ryandens.javaagent-test") version "0.12.2"
}
dependencies {
    val ktorVersion = "3.5.2"

    testJavaagent("com.beyondgrader.resource-agent:agent:2026.1.2")

    implementation(project(":lib"))

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.mongodb:mongodb-driver-sync:5.11.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    // netty arrives through ktor-server-netty, and ktor 3.5.2 (the newest) pins netty
    // 4.2.16.Final, which carries CVE-2026-75595 and CVE-2026-75596, fixed in 4.2.17.Final.
    // The BOM moves every netty module together; forcing netty-handler alone would leave its
    // siblings a version behind.
    implementation(platform("io.netty:netty-bom:4.2.18.Final"))

    testImplementation("io.kotest:kotest-runner-junit5:6.2.5")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.5")
}
tasks.shadowJar {
    // Shadow's KotlinModuleMetadataTransformer merges colliding META-INF/*.kotlin_module entries,
    // but only sees duplicates that reach it. The default EXCLUDE drops them first, which matters
    // here because two kotlin-logging artifacts are on the classpath: io.github.microutils, which
    // questioner declares, and io.github.oshai, which Jeed exports.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // Jeed ships a patched copy of one ktlint file under ktlint's own package, so five classes there
    // exist twice: KtlintKotlinCompiler, KtlintKotlinCompilerKt, LoggerFactory,
    // LoggerFactory$getLoggerInstance$1 and FormatPomModel. On a classpath Jeed's copy wins because
    // Jeed core precedes ktlint, but INCLUDE writes both entries and the JDK hands back the last one,
    // which is ktlint's, and ktlint's fails to initialize on Kotlin 2.4.20. First-wins for this
    // package restores the classpath ordering, as Jeed's own server does.
    filesMatching("com/pinterest/ktlint/rule/engine/core/api/**") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    // First-wins relies on Jeed core resolving ahead of ktlint-rule-engine-core, so check the jar
    // itself. TestShadowJarStartup catches the same failure, but only when the tests run, and
    // dockerBuild and dockerPush build the jar without them. MockComponentManager is the type Jeed's
    // patched copy names, where ktlint's own copy names MockProject.
    doLast {
        val facade = "com/pinterest/ktlint/rule/engine/core/api/KtlintKotlinCompilerKt.class"
        val copies = mutableListOf<ByteArray>()
        ZipInputStream(archiveFile.get().asFile.inputStream().buffered()).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: break
                if (entry.name == facade) {
                    copies.add(entries.readBytes())
                }
            }
        }
        check(copies.size == 1) {
            "Expected one $facade in the shaded jar, found ${copies.size}."
        }
        check(String(copies.single(), Charsets.ISO_8859_1).contains("MockComponentManager")) {
            "The shaded jar carries ktlint's $facade rather than Jeed's patched copy, so ktlint " +
                "would fail to initialize at run time. Check that Jeed core still resolves ahead " +
                "of ktlint-rule-engine-core."
        }
    }
    manifest {
        attributes["Launcher-Agent-Class"] = "com.beyondgrader.resourceagent.AgentKt"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}
application {
    mainClass.set("edu.illinois.cs.cs125.questioner.server.MainKt")
}
tasks.shadowJar {
    isZip64 = true
}
val dockerName = "cs124/questioner"
// One Sync rather than a Copy per file, so the build context holds exactly the current jar and the
// Dockerfile. Two Copy tasks left every earlier version's jar sitting there, and the Dockerfile's
// COPY *.jar fails as soon as a second one matches.
tasks.register<Sync>("dockerContext") {
    from(tasks.shadowJar)
    from("${projectDir}/Dockerfile")
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Exec>("dockerBuild") {
    dependsOn("dockerContext")
    workingDir(layout.buildDirectory.dir("docker"))
    environment("DOCKER_BUILDKIT", "1")
    commandLine(
        ("/usr/local/bin/docker build --pull . " +
            "-t ${dockerName}:latest " +
            "-t ${dockerName}:${project.version}").split(" ")
    )
}
tasks.register<Exec>("dockerPush") {
    dependsOn("dockerContext")
    workingDir(layout.buildDirectory.dir("docker"))
    commandLine(
        ("/usr/local/bin/docker buildx build --pull . --platform=linux/amd64,linux/arm64/v8 " +
            "--tag ${dockerName}:latest " +
            "--tag ${dockerName}:${project.version} --push").split(" ")
    )
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.test {
    useJUnitPlatform()
    dependsOn(":plugin:functionalTest")
    // TestShadowJarStartup boots the shaded jar the Docker image runs, since a jar resolves
    // duplicate entries differently from the classpath every other test sees.
    val shadowJarFile = tasks.shadowJar.flatMap { it.archiveFile }
    dependsOn(tasks.shadowJar)
    inputs.file(shadowJarFile)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider { listOf("-Dquestioner.shadowJar=${shadowJarFile.get().asFile.absolutePath}") },
    )
}
afterEvaluate {
    tasks.withType<FormatTask> {
        this.source = this.source.minus(fileTree("build")).asFileTree
    }
    tasks.withType<LintTask> {
        this.source = this.source.minus(fileTree("build")).asFileTree
    }
}

