import org.gradle.api.publish.plugins.PublishingPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    id("org.jmailen.kotlinter") version "5.7.0" apply false
    id("io.github.ben-manes.versions") version "0.61.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("com.autonomousapps.testkit") version "0.20" apply false
}
allprojects {
    group = "org.cs124.questioner"
    version = "2026.9.1"
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
        environment["JEED_FAIL_ON_REPLACED_STREAMS"] = "true"
        jvmArgs(
            "-ea", "--enable-preview", "-Dfile.encoding=UTF-8", "-Djava.security.manager=allow",
            "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:-OmitStackTraceInFastThrow",
            "-XX:+UnlockExperimentalVMOptions", "-XX:-VMContinuations",
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
    tasks.withType<LintTask> {
        dependsOn(tasks.withType<FormatTask>())
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
val publish = tasks.register("publish") {
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    description = "Publishes all artifacts to Maven Central, then closes and releases the staging repository."
    dependsOn("closeAndReleaseSonatypeStagingRepository")
}
subprojects {
    val subprojectPath = path
    plugins.withId("maven-publish") {
        publish.configure {
            dependsOn("$subprojectPath:publishToSonatype")
        }
    }
}
