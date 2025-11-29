package edu.illinois.cs.cs124.stumperd

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import edu.illinois.cs.cs125.questioner.lib.ResourceMonitoring
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.serialization.InstantSerializer
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import java.time.Instant

internal val logger = KotlinLogging.logger {}

private val serverStarted = Instant.now()

@Serializable
internal data class Status(
    val name: String = "stumperd",
    @Serializable(with = InstantSerializer::class)
    val started: Instant = serverStarted,
    val version: String = VERSION,
)

fun Application.stumperd() {
    routing {
        get("/") {
            call.respond(Status())
        }
        get("/version") {
            call.respond(VERSION)
        }
    }
}

fun main(): Unit = runBlocking {
    ResourceMonitoring.ensureAgentActivated()

    val logLevel = System.getenv("LOG_LEVEL")?.let { Level.toLevel(it) } ?: Level.INFO
    (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(logger.name).level = logLevel

    println("Starting stumperd server (log level $logLevel)")
    embeddedServer(Netty, port = 8888, module = Application::stumperd).start(wait = true)
}
