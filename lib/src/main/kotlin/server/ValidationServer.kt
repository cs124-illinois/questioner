package edu.illinois.cs.cs125.questioner.lib.server

import edu.illinois.cs.cs125.questioner.lib.ValidatorOptions
import edu.illinois.cs.cs125.questioner.lib.calibrate
import edu.illinois.cs.cs125.questioner.lib.validate
import edu.illinois.cs.cs125.questioner.lib.verifiers.toBase64
import edu.illinois.cs.cs125.questioner.lib.warm
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Simple socket server that processes validation/calibration requests.
 *
 * Protocol:
 * - Client sends: file path to .question.json
 * - Server responds: "ok" or "error:message"
 * - Client sends "shutdown" to stop the server
 *
 * Start with: java -cp <classpath> edu.illinois.cs.cs125.questioner.lib.server.ValidationServerKt <mode> <port> <rootDir> [options]
 */
object ValidationServer {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            System.err.println("Usage: ValidationServer <mode> <port> <rootDir> [maxMutationCount] [retries] [verbose]")
            System.exit(1)
        }

        val mode = args[0] // "validate" or "calibrate"
        val port = args[1].toInt()
        val rootDir = args[2]
        val maxMutationCount = args.getOrNull(3)?.toIntOrNull() ?: 256
        val retries = args.getOrNull(4)?.toIntOrNull() ?: 4
        val verbose = args.getOrNull(5)?.toBooleanStrictOrNull() ?: false

        val options = ValidatorOptions(
            maxMutationCount = maxMutationCount,
            retries = retries,
            verbose = verbose,
            rootDirectory = rootDir.toBase64(),
        )

        // Warm up before accepting requests
        println("ValidationServer ($mode) warming up...")
        runBlocking { warm() }
        println("ValidationServer ($mode) ready on port $port")

        ServerSocket(port).use { serverSocket ->
            // Write port to stdout so Gradle can read it (in case port was 0 for auto-select)
            println("PORT:${serverSocket.localPort}")
            System.out.flush()

            while (true) {
                val client = serverSocket.accept()
                handleClient(client, mode, options)
            }
        }
    }

    private fun handleClient(client: Socket, mode: String, options: ValidatorOptions) {
        client.use { socket ->
            val reader = socket.getInputStream().bufferedReader()
            val writer = PrintWriter(socket.getOutputStream(), true)

            val line = reader.readLine() ?: return

            if (line == "shutdown") {
                writer.println("ok")
                System.exit(0)
            }

            // Line is a file path
            val filePath = line.trim()
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
                val message = e.message?.replace("\n", " ")?.take(500) ?: "Unknown error"
                writer.println("error:$message")
            }
        }
    }
}
