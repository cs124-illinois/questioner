package edu.illinois.cs.cs125.questioner.lib.server

import edu.illinois.cs.cs125.questioner.lib.ValidatorOptions
import edu.illinois.cs.cs125.questioner.lib.calibrate
import edu.illinois.cs.cs125.questioner.lib.validate
import edu.illinois.cs.cs125.questioner.lib.verifiers.toBase64
import edu.illinois.cs.cs125.questioner.lib.warm
import kotlinx.coroutines.runBlocking
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Socket server that processes validation/calibration requests concurrently.
 *
 * Protocol:
 * - Client sends: file path to .question.json
 * - Server responds: "ok" or "error:message"
 * - Client sends "shutdown" to stop the server
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

    private fun buildErrorMessage(e: Throwable): String {
        val messages = mutableListOf<String>()

        // Walk the cause chain to find all messages
        var current: Throwable? = e
        while (current != null) {
            val msg = current.message
            if (!msg.isNullOrBlank()) {
                messages.add(msg)
            } else {
                // Include class name if no message
                messages.add(current::class.simpleName ?: "Unknown")
            }
            current = current.cause
            if (current == e) break // Prevent infinite loop
        }

        // If we still have nothing useful, include stack trace location
        if (messages.isEmpty() || messages.all { it == e::class.simpleName }) {
            val location = e.stackTrace.firstOrNull()?.let { "${it.fileName}:${it.lineNumber}" } ?: "unknown location"
            messages.add("${e::class.simpleName} at $location")
        }

        return messages.joinToString(" -> ")
            .replace("\n", " ")
            .replace("\r", "")
            .take(1000)
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
                runBlocking {
                    when (mode) {
                        "validate" -> filePath.validate(options)
                        "calibrate" -> filePath.calibrate(options)
                        else -> throw IllegalArgumentException("Unknown mode: $mode")
                    }
                }
                writer.println("ok")
            } catch (e: Throwable) {
                val message = buildErrorMessage(e)
                writer.println("error:$message")
            } finally {
                semaphore.release()
            }
        }
    }
}
