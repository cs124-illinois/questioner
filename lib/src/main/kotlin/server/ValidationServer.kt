package edu.illinois.cs.cs125.questioner.lib.server

import edu.illinois.cs.cs125.questioner.lib.CachePoisonedException
import edu.illinois.cs.cs125.questioner.lib.CalibrateResult
import edu.illinois.cs.cs125.questioner.lib.ValidateResult
import edu.illinois.cs.cs125.questioner.lib.ValidationPhase
import edu.illinois.cs.cs125.questioner.lib.ValidationResult
import edu.illinois.cs.cs125.questioner.lib.ValidationResultFormatter
import edu.illinois.cs.cs125.questioner.lib.ValidatorOptions
import edu.illinois.cs.cs125.questioner.lib.calibrateWithResult
import edu.illinois.cs.cs125.questioner.lib.report
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.toFailureResult
import edu.illinois.cs.cs125.questioner.lib.toPhase1SuccessResult
import edu.illinois.cs.cs125.questioner.lib.toSuccessResult
import edu.illinois.cs.cs125.questioner.lib.validateWithResult
import edu.illinois.cs.cs125.questioner.lib.verifiers.toBase64
import edu.illinois.cs.cs125.questioner.lib.warm
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Socket server that processes validation/calibration requests concurrently.
 *
 * Protocol:
 * - Client sends: file path to .question.json
 * - Server responds: "result:" + Base64(JSON(ValidationResult))
 * - For skipped questions: "skipped:" + questionName
 * - Client sends "shutdown" to stop the server, server responds "ok"
 *
 * Start with: java -cp <classpath> edu.illinois.cs.cs125.questioner.lib.server.ValidationServerKt <mode> <port> <rootDir> [options]
 */
object ValidationServer {
    private val shuttingDown = AtomicBoolean(false)
    private val lastActivityTime = AtomicLong(System.currentTimeMillis())

    // Default idle timeout: 1 hour in milliseconds
    private const val DEFAULT_IDLE_TIMEOUT_MS = 60 * 60 * 1000L

    private fun updateLastActivity() {
        lastActivityTime.set(System.currentTimeMillis())
    }

    private fun startIdleTimeoutChecker(idleTimeoutMs: Long, scheduler: ScheduledExecutorService) {
        scheduler.scheduleAtFixedRate({
            val idleTime = System.currentTimeMillis() - lastActivityTime.get()
            if (idleTime >= idleTimeoutMs) {
                println("ValidationServer idle for ${idleTime / 1000}s, shutting down...")
                shuttingDown.set(true)
                System.exit(0)
            }
        }, 1, 1, TimeUnit.MINUTES)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            System.err.println("Usage: ValidationServer <mode> <port> <rootDir> [maxMutationCount] [retries] [verbose] [concurrency] [idleTimeoutMinutes]")
            System.exit(1)
        }

        val mode = args[0] // "validate" or "calibrate"
        val port = args[1].toInt()
        val rootDir = args[2]
        val maxMutationCount = args.getOrNull(3)?.toIntOrNull() ?: 256
        val retries = args.getOrNull(4)?.toIntOrNull() ?: 4
        val verbose = args.getOrNull(5)?.toBooleanStrictOrNull() ?: false
        val concurrency = args.getOrNull(6)?.toIntOrNull() ?: 8
        val idleTimeoutMinutes = args.getOrNull(7)?.toIntOrNull() ?: 60
        val idleTimeoutMs = idleTimeoutMinutes * 60 * 1000L

        val options = ValidatorOptions(
            maxMutationCount = maxMutationCount,
            retries = retries,
            verbose = verbose,
            rootDirectory = rootDir.toBase64(),
        )

        // Semaphore to limit concurrent validations
        val semaphore = Semaphore(concurrency)

        // Thread pool for handling clients
        val executor = Executors.newCachedThreadPool()

        // Warm up before accepting requests
        println("ValidationServer ($mode) warming up...")
        runBlocking { warm() }
        println("ValidationServer ($mode) ready, concurrency=$concurrency, idleTimeout=${idleTimeoutMinutes}min")

        // Start idle timeout checker
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        startIdleTimeoutChecker(idleTimeoutMs, scheduler)

        ServerSocket(port).use { serverSocket ->
            // Write port to stdout so Gradle can read it (in case port was 0 for auto-select)
            println("PORT:${serverSocket.localPort}")
            System.out.flush()

            while (!shuttingDown.get()) {
                try {
                    val client = serverSocket.accept()
                    executor.submit {
                        handleClient(client, mode, options, semaphore)
                    }
                } catch (e: Exception) {
                    if (!shuttingDown.get()) {
                        System.err.println("Error accepting client: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleClient(client: Socket, mode: String, options: ValidatorOptions, semaphore: Semaphore) {
        updateLastActivity()
        client.use { socket ->
            val reader = socket.getInputStream().bufferedReader()
            val writer = PrintWriter(socket.getOutputStream(), true)

            val line = reader.readLine() ?: return

            if (line == "shutdown") {
                writer.println("ok")
                shuttingDown.set(true)
                // Give time for response to be sent
                Thread.sleep(100)
                System.exit(0)
            }

            // Line is a file path
            val filePath = line.trim()

            // Acquire semaphore to limit concurrency
            semaphore.acquire()
            try {
                val startTime = System.currentTimeMillis()
                val result = runBlocking {
                    when (mode) {
                        "validate" -> handleValidation(filePath, options, startTime)
                        "calibrate" -> handleCalibration(filePath, options, startTime)
                        else -> throw IllegalArgumentException("Unknown mode: $mode")
                    }
                }

                when (result) {
                    is HandleResult.Result -> {
                        // Generate per-question report in a subdirectory named after the hash
                        // filePath is like .../build/questioner/questions/{hash}.parsed.json
                        val questionFile = File(filePath)
                        val hash = questionFile.nameWithoutExtension.removeSuffix(".parsed")
                        val reportDir = questionFile.parentFile.resolve(hash)
                        reportDir.mkdirs()
                        val reportPath = reportDir.resolve("report.html")
                        reportPath.writeText(ValidationResultFormatter.formatHtml(result.validationResult))

                        // Send JSON result
                        val jsonResult = json.encodeToString(ValidationResult.serializer(), result.validationResult)
                        val base64Result = Base64.getEncoder().encodeToString(jsonResult.toByteArray(Charsets.UTF_8))
                        writer.println("result:$base64Result")

                        // Log the result
                        when (val vr = result.validationResult) {
                            is ValidationResult.Success -> {
                                println("${vr.questionName}: ${mode.replaceFirstChar { it.uppercase() }} complete (${vr.summary.retries} retries)")
                            }
                            is ValidationResult.Failure -> {
                                println("FAILED ${vr.questionName}: ${mode.replaceFirstChar { it.uppercase() }} (${vr.error.errorType})")
                            }
                        }
                    }
                    is HandleResult.Skipped -> {
                        writer.println("skipped:${result.questionName}")
                        println("SKIPPED ${result.questionName}: ${mode.replaceFirstChar { it.uppercase() }} already complete")
                    }
                }
            } catch (e: Throwable) {
                // Unexpected error - create a failure result
                val errorResult = e.toFailureResult(
                    questionPath = filePath,
                    questionName = File(filePath).nameWithoutExtension,
                    questionAuthor = "unknown",
                    questionSlug = "unknown",
                    phase = if (mode == "validate") ValidationPhase.VALIDATE else ValidationPhase.CALIBRATE,
                    startTime = System.currentTimeMillis(),
                )
                val jsonResult = json.encodeToString(ValidationResult.serializer(), errorResult)
                val base64Result = Base64.getEncoder().encodeToString(jsonResult.toByteArray(Charsets.UTF_8))
                writer.println("result:$base64Result")
                System.err.println("Unexpected error during $mode: ${e.message}")

                // CachePoisonedException means the JVM's class cache is permanently corrupted.
                // Exit so the manager can start a fresh server process.
                if (e is CachePoisonedException) {
                    System.err.println("JVM class cache poisoned, exiting to allow restart...")
                    shuttingDown.set(true)
                    Thread.sleep(100)
                    System.exit(1)
                }
            } finally {
                semaphore.release()
            }
        }
    }

    private sealed class HandleResult {
        data class Result(val validationResult: ValidationResult) : HandleResult()
        data class Skipped(val questionName: String) : HandleResult()
    }

    private suspend fun handleValidation(filePath: String, options: ValidatorOptions, startTime: Long): HandleResult {
        return when (val result = filePath.validateWithResult(options)) {
            is ValidateResult.Success -> {
                val validationResult = result.question.toPhase1SuccessResult(
                    questionPath = filePath,
                    startTime = startTime,
                    seed = result.seed,
                    retries = result.retries,
                )
                HandleResult.Result(validationResult)
            }
            is ValidateResult.Failure -> {
                // Also write the old-style report for compatibility
                val reportPath = Path.of(filePath).parent.resolve("report.html")
                reportPath.toFile().writeText(result.error.report(result.question))

                val validationResult = result.error.toFailureResult(
                    questionPath = filePath,
                    questionName = result.question.published.name,
                    questionAuthor = result.question.published.author,
                    questionSlug = result.question.published.path,
                    phase = ValidationPhase.VALIDATE,
                    startTime = startTime,
                )
                HandleResult.Result(validationResult)
            }
            is ValidateResult.Skipped -> HandleResult.Skipped(result.questionName)
        }
    }

    private suspend fun handleCalibration(filePath: String, options: ValidatorOptions, startTime: Long): HandleResult {
        return when (val result = filePath.calibrateWithResult(options)) {
            is CalibrateResult.Success -> {
                val validationResult = result.report.toSuccessResult(
                    questionPath = filePath,
                    startTime = startTime,
                )
                HandleResult.Result(validationResult)
            }
            is CalibrateResult.Failure -> {
                // Also write the old-style report for compatibility
                val reportPath = Path.of(filePath).parent.resolve("report.html")
                reportPath.toFile().writeText(result.error.report(result.question))

                val validationResult = result.error.toFailureResult(
                    questionPath = filePath,
                    questionName = result.question.published.name,
                    questionAuthor = result.question.published.author,
                    questionSlug = result.question.published.path,
                    phase = ValidationPhase.CALIBRATE,
                    startTime = startTime,
                )
                HandleResult.Result(validationResult)
            }
            is CalibrateResult.Skipped -> HandleResult.Skipped(result.questionName)
        }
    }
}
