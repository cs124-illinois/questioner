package edu.illinois.cs.cs125.questioner.plugin

import org.gradle.api.Project
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    private val progressLoggerFactory: ProgressLoggerFactory,
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

    // Single progress tracker for all validation
    private var progressLogger: ProgressLogger? = null
    private val completed = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    private val failures = ConcurrentLinkedQueue<ValidationFailure>()

    data class ValidationFailure(val filePath: String, val phase: String, val message: String)

    @Synchronized
    fun getValidatePort(): Int {
        if (validateServerPort == null) {
            startServer("validate", commonJvmArgs).let { (process, port) ->
                validateServerProcess = process
                validateServerPort = port
                registerShutdownHook(process, port, "validate")
            }
        }
        ensureProgressStarted()
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

    @Synchronized
    private fun ensureProgressStarted() {
        if (progressLogger == null) {
            progressLogger = progressLoggerFactory.newOperation(ValidationServerManager::class.java).also {
                it.description = "Validating questions"
                it.started()
                it.progress("0/$totalQuestions")
            }
        }
    }

    fun questionCompleted(success: Boolean, filePath: String? = null, phase: String? = null, errorMessage: String? = null) {
        if (success) {
            completed.incrementAndGet()
        } else {
            failed.incrementAndGet()
            if (filePath != null && phase != null && errorMessage != null) {
                failures.add(ValidationFailure(filePath, phase, errorMessage))
            }
        }
        updateProgress()
    }

    private fun updateProgress() {
        val done = completed.get()
        val failCount = failed.get()
        val total = totalQuestions

        val progressBar = buildProgressBar(done + failCount, total, 20)
        val status = buildString {
            append("$progressBar ${done + failCount}/$total")
            if (failCount > 0) append(" ($failCount failed)")
        }
        progressLogger?.progress(status)
    }

    private fun buildProgressBar(current: Int, total: Int, width: Int): String {
        if (total == 0) return "[${"=".repeat(width)}]"
        val filled = (current.toDouble() / total * width).toInt().coerceIn(0, width)
        val empty = width - filled
        return "[" + "=".repeat(filled) + " ".repeat(empty) + "]"
    }

    fun printReport(): Boolean {
        val successCount = completed.get()
        val failCount = failed.get()
        val total = successCount + failCount

        println()
        println("=".repeat(60))
        println("Validation Summary")
        println("=".repeat(60))
        println("  Total:     $total")
        println("  Succeeded: $successCount")
        println("  Failed:    $failCount")

        if (failures.isNotEmpty()) {
            println()
            println("Failures:")
            failures.forEach { failure ->
                val shortPath = failure.filePath.substringAfterLast("/")
                println("  - $shortPath (${failure.phase})")
                println("    ${failure.message.take(200)}")
            }

            // Generate and open HTML report
            val reportFile = generateHtmlReport(successCount, failCount)
            println()
            println("Full report: file://${reportFile.absolutePath}")
            openInBrowser(reportFile)
        }
        println("=".repeat(60))
        println()

        return failCount == 0
    }

    private fun generateHtmlReport(successCount: Int, failCount: Int): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val total = successCount + failCount
        val successPercent = if (total > 0) (successCount * 100.0 / total).toInt() else 100

        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("  <title>Validation Report - $failCount failures</title>")
            appendLine("  <style>")
            appendLine(CSS_STYLES)
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <div class=\"container\">")
            appendLine("    <header>")
            appendLine("      <h1>Validation Report</h1>")
            appendLine("      <p class=\"timestamp\">$timestamp</p>")
            appendLine("    </header>")
            appendLine()
            appendLine("    <div class=\"summary\">")
            appendLine("      <div class=\"stat\">")
            appendLine("        <div class=\"stat-value\">$total</div>")
            appendLine("        <div class=\"stat-label\">Total</div>")
            appendLine("      </div>")
            appendLine("      <div class=\"stat success\">")
            appendLine("        <div class=\"stat-value\">$successCount</div>")
            appendLine("        <div class=\"stat-label\">Passed</div>")
            appendLine("      </div>")
            appendLine("      <div class=\"stat failure\">")
            appendLine("        <div class=\"stat-value\">$failCount</div>")
            appendLine("        <div class=\"stat-label\">Failed</div>")
            appendLine("      </div>")
            appendLine("      <div class=\"stat\">")
            appendLine("        <div class=\"stat-value\">$successPercent%</div>")
            appendLine("        <div class=\"stat-label\">Pass Rate</div>")
            appendLine("      </div>")
            appendLine("    </div>")
            appendLine()
            appendLine("    <div class=\"progress-bar\">")
            appendLine("      <div class=\"progress-fill\" style=\"width: $successPercent%\"></div>")
            appendLine("    </div>")
            appendLine()

            if (failures.isNotEmpty()) {
                appendLine("    <h2>Failures</h2>")
                appendLine("    <div class=\"failures\">")
                failures.forEach { failure ->
                    val shortPath = failure.filePath.substringAfterLast("/")
                    val questionName = shortPath.removeSuffix(".question.json")
                    val escapedMessage = failure.message
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")

                    appendLine("      <div class=\"failure-card\">")
                    appendLine("        <div class=\"failure-header\">")
                    appendLine("          <span class=\"failure-badge\">${failure.phase}</span>")
                    appendLine("          <span class=\"failure-name\">$questionName</span>")
                    appendLine("        </div>")
                    appendLine("        <div class=\"failure-details\">")
                    appendLine("          <div class=\"failure-path\">")
                    appendLine("            <code>${failure.filePath}</code>")
                    appendLine("          </div>")
                    appendLine("          <pre class=\"failure-message\">$escapedMessage</pre>")
                    appendLine("        </div>")
                    appendLine("      </div>")
                }
                appendLine("    </div>")
            }

            appendLine("  </div>")
            appendLine("</body>")
            appendLine("</html>")
        }

        val reportDir = File(project.layout.buildDirectory.get().asFile, "questioner")
        reportDir.mkdirs()
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
        progressLogger?.completed()
        progressLogger = null

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

        private val CSS_STYLES = """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                background: #1a1a2e;
                color: #eee;
                line-height: 1.6;
                padding: 2rem;
            }
            .container { max-width: 1000px; margin: 0 auto; }
            header { margin-bottom: 2rem; }
            h1 { font-size: 2rem; font-weight: 600; }
            h2 { font-size: 1.4rem; margin: 2rem 0 1rem; color: #ff6b6b; }
            .timestamp { color: #888; font-size: 0.9rem; margin-top: 0.5rem; }
            .summary {
                display: grid;
                grid-template-columns: repeat(4, 1fr);
                gap: 1rem;
                margin-bottom: 1.5rem;
            }
            .stat {
                background: #16213e;
                padding: 1.5rem;
                border-radius: 12px;
                text-align: center;
            }
            .stat-value { font-size: 2.5rem; font-weight: 700; }
            .stat-label { color: #888; font-size: 0.9rem; text-transform: uppercase; }
            .stat.success .stat-value { color: #4ade80; }
            .stat.failure .stat-value { color: #ff6b6b; }
            .progress-bar {
                height: 8px;
                background: #ff6b6b;
                border-radius: 4px;
                overflow: hidden;
                margin-bottom: 2rem;
            }
            .progress-fill { height: 100%; background: #4ade80; transition: width 0.3s; }
            .failures { display: flex; flex-direction: column; gap: 1rem; }
            .failure-card {
                background: #16213e;
                border-radius: 8px;
                overflow: hidden;
                border-left: 4px solid #ff6b6b;
            }
            .failure-header {
                padding: 1rem 1.5rem;
                display: flex;
                align-items: center;
                gap: 1rem;
                border-bottom: 1px solid #2a3a5e;
            }
            .failure-badge {
                background: #ff6b6b;
                color: #1a1a2e;
                padding: 0.25rem 0.75rem;
                border-radius: 4px;
                font-size: 0.75rem;
                font-weight: 600;
                text-transform: uppercase;
            }
            .failure-name { font-weight: 500; font-family: monospace; font-size: 1.1rem; }
            .failure-details { padding: 1.5rem; background: #0f1729; }
            .failure-path { margin-bottom: 1rem; }
            .failure-path code {
                background: #1a2744;
                padding: 0.5rem 0.75rem;
                border-radius: 4px;
                font-size: 0.85rem;
                display: inline-block;
            }
            .failure-message {
                background: #1a2744;
                padding: 1rem;
                border-radius: 8px;
                overflow-x: auto;
                font-size: 0.85rem;
                white-space: pre-wrap;
                word-break: break-word;
                margin: 0;
                max-height: 400px;
                overflow-y: auto;
            }
            @media (max-width: 768px) {
                .summary { grid-template-columns: repeat(2, 1fr); }
                body { padding: 1rem; }
            }
        """.trimIndent()

        @Synchronized
        fun initialize(
            project: Project,
            progressLoggerFactory: ProgressLoggerFactory,
            commonJvmArgs: List<String>,
            noJitJvmArgs: List<String>,
            rootDir: String,
            maxMutationCount: Int,
            retries: Int,
            verbose: Boolean,
            concurrency: Int,
            totalQuestions: Int,
            idleTimeoutMinutes: Int = 60,
        ): ValidationServerManager = managers.getOrPut(project.rootProject) {
            ValidationServerManager(
                project.rootProject,
                progressLoggerFactory,
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

        @Synchronized
        fun getInstance(project: Project): ValidationServerManager? = managers[project.rootProject]
    }
}
