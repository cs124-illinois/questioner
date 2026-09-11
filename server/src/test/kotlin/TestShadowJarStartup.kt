package edu.illinois.cs.cs125.questioner.server

import com.google.common.truth.Truth.assertWithMessage
import io.kotest.core.spec.style.StringSpec
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes

private const val SERVER_STARTING = "Starting questioner server"

// The JVM flags from the Dockerfile's CMD, so the jar starts the way the container starts it.
private val dockerJvmFlags = listOf(
    "-ea", "--enable-preview", "-Dfile.encoding=UTF-8",
    "-Djava.security.manager=allow", "-XX:-OmitStackTraceInFastThrow",
    "-XX:+UnlockExperimentalVMOptions", "-XX:-VMContinuations",
    "-Xss256k", "-XX:+UseZGC", "-XX:+ZGenerational",
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    "--add-exports", "java.management/sun.management=ALL-UNNAMED",
)

/**
 * Boots the shaded jar the Docker image runs and waits for the server to get past warming Jeed.
 *
 * Every other test runs against a classpath, where Jeed core precedes its dependencies and so wins
 * any class name the two share. A jar has no such rule: the JDK hands back the last of two entries
 * with the same name. Jeed carries a patched copy of ktlint's KtlintKotlinCompiler, and when the
 * shaded jar held both copies ktlint's won, so the container died during startup with "Extensions
 * storage is not registered" while the whole suite stayed green.
 *
 * It stops at the log line that follows warming. After that the server loads its warm question
 * from MongoDB, which this test does not provide.
 */
class TestShadowJarStartup :
    StringSpec({
        "shaded jar should start and warm Jeed".config(timeout = 5.minutes) {
            val jar = File(System.getProperty("questioner.shadowJar") ?: error("questioner.shadowJar is not set"))
            check(jar.isFile) { "Shaded jar not found at $jar" }

            val java = File(System.getProperty("java.home"), "bin/java").path
            val process = ProcessBuilder(listOf(java) + dockerJvmFlags + listOf("-jar", jar.path))
                .redirectErrorStream(true)
                .apply {
                    environment()["QUESTIONER_MAX_CONCURRENCY"] = "1"
                    environment()["QUESTIONER_TEST_TIMEOUT_MS"] = "1000"
                    environment()["QUESTIONER_TESTTEST_TIMEOUT_MS"] = "1000"
                    environment()["JEED_FAIL_ON_REPLACED_STREAMS"] = "true"
                }
                .start()

            val output = StringBuffer()
            val started = CompletableFuture<Boolean>()
            thread(isDaemon = true) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.appendLine(line)
                        if (line.contains(SERVER_STARTING)) {
                            started.complete(true)
                        }
                    }
                }
                started.complete(false)
            }

            val reachedServerStart = try {
                started.get(3, TimeUnit.MINUTES)
            } catch (_: TimeoutException) {
                false
            } finally {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly().waitFor(30, TimeUnit.SECONDS)
            }

            assertWithMessage("The shaded jar did not get past warming Jeed. Its output:\n$output")
                .that(reachedServerStart)
                .isTrue()
        }
    })
