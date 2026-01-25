package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.dotenv
import org.gradle.api.Project
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages ValidationServer processes for a Gradle build.
 *
 * Starts servers on demand and shuts them down when Gradle exits.
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
) {
    private var validateServerPort: Int? = null
    private var validateServerProcess: Process? = null

    private var calibrateServerPort: Int? = null
    private var calibrateServerProcess: Process? = null

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
            )

        project.logger.info("Starting $mode server: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
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
        ): ValidationServerManager = managers.getOrPut(project.rootProject) {
            ValidationServerManager(
                project.rootProject,
                commonJvmArgs,
                noJitJvmArgs,
                rootDir,
                maxMutationCount,
                retries,
                verbose,
                concurrency,
            )
        }

        @Synchronized
        fun getInstance(project: Project): ValidationServerManager? = managers[project.rootProject]
    }
}
