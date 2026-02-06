package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.ValidationResult
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import java.io.PrintWriter
import java.net.Socket
import java.util.Base64

/**
 * Result of a validation request to the server.
 */
sealed class ValidationResponse {
    /**
     * Validation completed (success or failure) with full result.
     */
    data class Completed(val result: ValidationResult) : ValidationResponse()

    /**
     * Validation was skipped because it was already done.
     */
    data class Skipped(val questionName: String) : ValidationResponse()

    /**
     * Server's JVM is corrupted and restarting. Client should retry.
     */
    data class Restart(val message: String) : ValidationResponse()
}

/**
 * Client for communicating with ValidationServer.
 */
object ValidationClient {
    /**
     * Send a validation request and receive a full ValidationResult.
     */
    fun sendRequest(port: Int, filePath: String): Result<ValidationResponse> = try {
        Socket("localhost", port).use { socket ->
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = socket.getInputStream().bufferedReader()

            writer.println(filePath)
            val response = reader.readLine()

            when {
                response.startsWith("result:") -> {
                    val base64Json = response.removePrefix("result:")
                    val jsonBytes = Base64.getDecoder().decode(base64Json)
                    val jsonString = String(jsonBytes, Charsets.UTF_8)
                    val result = json.decodeFromString(ValidationResult.serializer(), jsonString)
                    Result.success(ValidationResponse.Completed(result))
                }

                response.startsWith("skipped:") -> {
                    val questionName = response.removePrefix("skipped:")
                    Result.success(ValidationResponse.Skipped(questionName))
                }

                response.startsWith("restart:") -> {
                    val message = response.removePrefix("restart:")
                    Result.success(ValidationResponse.Restart(message))
                }

                // Legacy response support (for backward compatibility)
                response == "ok" -> Result.failure(
                    ValidationException("Legacy 'ok' response received - server needs update"),
                )

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
