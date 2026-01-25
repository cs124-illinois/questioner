package edu.illinois.cs.cs125.questioner.plugin

import java.io.PrintWriter
import java.net.Socket

/**
 * Client for communicating with ValidationServer.
 */
object ValidationClient {
    fun sendRequest(port: Int, filePath: String): Result<Unit> = try {
        Socket("localhost", port).use { socket ->
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = socket.getInputStream().bufferedReader()

            writer.println(filePath)
            val response = reader.readLine()

            when {
                response == "ok" -> Result.success(Unit)

                response.startsWith("error:") -> Result.failure(
                    ValidationException(response.removePrefix("error:")),
                )

                else -> Result.failure(
                    ValidationException("Unexpected response: $response"),
                )
            }
        }
    } catch (e: Exception) {
        Result.failure(ValidationException("Failed to connect to validation server: ${e.message}"))
    }

    fun shutdown(port: Int) {
        try {
            Socket("localhost", port).use { socket ->
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println("shutdown")
            }
        } catch (_: Exception) {
            // Server may already be down
        }
    }
}

class ValidationException(message: String) : RuntimeException(message)
