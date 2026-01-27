package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.ValidationResult
import edu.illinois.cs.cs125.questioner.lib.ValidationResultFormatter
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import org.gradle.api.Project
import java.awt.Desktop
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages ValidationServer processes for a Gradle build.
 *
 * Starts servers on demand. Servers automatically shut down after idle timeout (default 1 hour).
 * Also tracks and displays progress using Gradle's ProgressLogger.
 */
class ValidationServerManager(
    private val project: Project,
    private val commonJvmArgs: List<String>,
    private val noJitJvmArgs: List<String>,
    private val rootDir: String,
    private val maxMutationCount: Int,
    private val retries: Int,
    private val verbose: Boolean,
    private val concurrency: Int,
    private val totalQuestions: Int,
    private val idleTimeoutMinutes: Int = 60,
) {
    private var validateServerPort: Int? = null
    private var validateServerProcess: Process? = null

    private var calibrateServerPort: Int? = null
    private var calibrateServerProcess: Process? = null

    // Track validation results - store full ValidationResult objects
    private val completed = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    private val skipped = AtomicInteger(0)
    private val results = ConcurrentLinkedQueue<ValidationResult>()

    // Legacy type for backward compatibility - can be removed after migration
    data class ValidationFailure(val filePath: String, val phase: String, val message: String)

    private fun getQuestionDisplayName(filePath: String): String = try {
        val question = json.decodeFromString<Question>(File(filePath).readText())
        val publishedPath = question.published.path
        val correctPath = question.correctPath
        if (correctPath != null) {
            "$publishedPath ($correctPath)"
        } else {
            publishedPath
        }
    } catch (e: Exception) {
        // Fallback to hash if we can't read the file
        filePath.substringAfterLast("/").removeSuffix(".parsed.json")
    }

    @Synchronized
    fun getValidatePort(): Int {
        if (validateServerPort == null) {
            startServer("validate", commonJvmArgs).let { (process, port) ->
                validateServerProcess = process
                validateServerPort = port
                registerShutdownHook(process, port, "validate")
            }
        }
        return validateServerPort!!
    }

    @Synchronized
    fun getCalibratePort(): Int {
        if (calibrateServerPort == null) {
            startServer("calibrate", commonJvmArgs + noJitJvmArgs).let { (process, port) ->
                calibrateServerProcess = process
                calibrateServerPort = port
                registerShutdownHook(process, port, "calibrate")
            }
        }
        return calibrateServerPort!!
    }

    /**
     * Record a validation result.
     */
    fun recordResult(result: ValidationResult) {
        results.add(result)
        when (result) {
            is ValidationResult.Success -> completed.incrementAndGet()
            is ValidationResult.Failure -> failed.incrementAndGet()
        }
    }

    /**
     * Record a skipped question.
     */
    fun recordSkipped() {
        skipped.incrementAndGet()
    }

    /**
     * Legacy method for backward compatibility - records a simple success/failure.
     */
    fun questionCompleted(success: Boolean, filePath: String? = null, phase: String? = null, errorMessage: String? = null) {
        if (success) {
            completed.incrementAndGet()
        } else {
            failed.incrementAndGet()
            // Note: We no longer store failures in the old format when using the new API
            // This method is kept for backward compatibility but should be phased out
        }
    }

    fun printReport(): Boolean {
        val successCount = completed.get()
        val failCount = failed.get()
        val skipCount = skipped.get()
        val total = successCount + failCount

        println()
        println("=".repeat(60))
        println("Validation Summary")
        println("=".repeat(60))
        println("  Total:     $total")
        println("  Succeeded: $successCount")
        println("  Failed:    $failCount")
        if (skipCount > 0) {
            println("  Skipped:   $skipCount")
        }

        val allResults = results.toList()
        val failures = allResults.filterIsInstance<ValidationResult.Failure>()

        if (failures.isNotEmpty()) {
            println()
            println("Failures:")
            failures.forEach { failure ->
                println("  - ${failure.questionDisplayName()} (${failure.phase.name.lowercase()})")
                // Show file path as clickable URL
                failure.error.sourceFilePath?.let { path ->
                    val fullPath = File(project.rootProject.projectDir, path).absolutePath
                    println("    file://$fullPath")
                }
                println("    ${failure.error.errorType}: ${failure.error.message.take(150)}")
            }
        }

        // Always generate the summary report (even with no failures) if we have results
        if (allResults.isNotEmpty()) {
            val reportFile = generateLinkedHtmlReport(allResults)
            println()
            println("Full report: file://${reportFile.absolutePath}")
            if (failures.isNotEmpty()) {
                openInBrowser(reportFile)
            }
        }
        println("=".repeat(60))
        println()

        return failCount == 0
    }

    /**
     * Generate an HTML summary report with links to individual question reports.
     */
    private fun generateLinkedHtmlReport(allResults: List<ValidationResult>): File {
        val reportDir = File(project.layout.buildDirectory.get().asFile, "questioner")
        reportDir.mkdirs()

        val html = ValidationResultFormatter.formatSummaryHtml(allResults)

        val reportFile = File(reportDir, "validation-report.html")
        reportFile.writeText(html)
        return reportFile
    }

    private fun openInBrowser(file: File) {
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> {
                    Runtime.getRuntime().exec(arrayOf("open", file.absolutePath))
                }

                os.contains("win") -> {
                    Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", file.absolutePath))
                }

                os.contains("nix") || os.contains("nux") -> {
                    Runtime.getRuntime().exec(arrayOf("xdg-open", file.absolutePath))
                }

                else -> {
                    // Fallback to Desktop API
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(file.toURI())
                    }
                }
            }
        } catch (e: Exception) {
            project.logger.warn("Could not open report in browser: ${e.message}")
        }
    }

    @Synchronized
    fun shutdown() {
        // Shutdown servers
        validateServerPort?.let { port ->
            try {
                ValidationClient.shutdown(port)
                validateServerProcess?.waitFor(5, TimeUnit.SECONDS)
            } catch (_: Exception) {
            }
            validateServerProcess?.destroyForcibly()
            validateServerProcess = null
            validateServerPort = null
        }

        calibrateServerPort?.let { port ->
            try {
                ValidationClient.shutdown(port)
                calibrateServerProcess?.waitFor(5, TimeUnit.SECONDS)
            } catch (_: Exception) {
            }
            calibrateServerProcess?.destroyForcibly()
            calibrateServerProcess = null
            calibrateServerPort = null
        }
    }

    private fun startServer(mode: String, jvmArgs: List<String>): Pair<Process, Int> {
        val classpath = project.configurations.getByName("runtimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .map { it.file.absolutePath }
            .joinToString(File.pathSeparator)

        val javaHome = System.getProperty("java.home")
        val javaExec = "$javaHome/bin/java"

        val command = listOf(javaExec) +
            jvmArgs +
            listOf(
                "-cp", classpath,
                "edu.illinois.cs.cs125.questioner.lib.server.ValidationServer",
                mode,
                "0", // Auto-select port
                rootDir,
                maxMutationCount.toString(),
                retries.toString(),
                verbose.toString(),
                concurrency.toString(),
                idleTimeoutMinutes.toString(),
            )

        project.logger.info("Starting $mode server: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply {
                environment()["JEED_USE_CACHE"] = "true"
            }
            .start()

        // Read output until we get the port
        val reader = process.inputStream.bufferedReader()
        var port: Int? = null

        while (port == null) {
            val line = reader.readLine() ?: throw RuntimeException("Server exited before reporting port")
            project.logger.info("[$mode server] $line")
            if (line.startsWith("PORT:")) {
                port = line.removePrefix("PORT:").toInt()
            }
        }

        // Continue reading output in background
        Thread {
            reader.lineSequence().forEach { line ->
                project.logger.info("[$mode server] $line")
            }
        }.start()

        return process to port
    }

    private fun registerShutdownHook(process: Process, port: Int, mode: String) {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    ValidationClient.shutdown(port)
                    process.waitFor(5, TimeUnit.SECONDS)
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                } catch (_: Exception) {
                    process.destroyForcibly()
                }
            },
        )
    }

    companion object {
        private val managers = mutableMapOf<Project, ValidationServerManager>()

        @Synchronized
        fun initialize(
            project: Project,
            commonJvmArgs: List<String>,
            noJitJvmArgs: List<String>,
            rootDir: String,
            maxMutationCount: Int,
            retries: Int,
            verbose: Boolean,
            concurrency: Int,
            totalQuestions: Int,
            idleTimeoutMinutes: Int = 60,
            restartServers: Boolean = false,
        ): ValidationServerManager {
            val rootProject = project.rootProject

            // If restartServers is true, shutdown any existing manager first
            if (restartServers) {
                managers[rootProject]?.let { existing ->
                    project.logger.lifecycle("Restarting validation servers...")
                    existing.shutdown()
                    managers.remove(rootProject)
                }
            }

            return managers.getOrPut(rootProject) {
                ValidationServerManager(
                    rootProject,
                    commonJvmArgs,
                    noJitJvmArgs,
                    rootDir,
                    maxMutationCount,
                    retries,
                    verbose,
                    concurrency,
                    totalQuestions,
                    idleTimeoutMinutes,
                )
            }
        }

        @Synchronized
        fun getInstance(project: Project): ValidationServerManager? = managers[project.rootProject]
    }
}
