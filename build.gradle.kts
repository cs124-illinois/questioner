import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("org.jmailen.kotlinter") version "5.3.0" apply false
    id("com.github.ben-manes.versions") version "0.53.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}
allprojects {
    group = "org.cs124.questioner"
    version = "2025.12.0"
}
subprojects {
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    tasks.withType<Sign> {
        onlyIf {
            gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
        }
        isRequired = gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }
    tasks.withType<Test> {
        useJUnitPlatform()
        environment["JEED_USE_CACHE"] = "true"
        jvmArgs(
            "-ea", "--enable-preview", "-Dfile.encoding=UTF-8", "-Djava.security.manager=allow",
            "-XX:+UseZGC", "-XX:ZCollectionInterval=8", "-XX:-OmitStackTraceInFastThrow",
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
}
allprojects {
    tasks.withType<Test> {
        enableAssertions = true
    }
}
tasks.dependencyUpdates {
    fun String.isNonStable() = !(
        listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) }
            || "^[0-9,.v-]+(-r)?$".toRegex().matches(this)
        )
    rejectVersionIf { candidate.version.isNonStable() }
    gradleReleaseChannel = "current"
}
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
