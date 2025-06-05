package edu.illinois.cs.cs125.questioner.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.ryanharter.ktor.moshi.moshi
import com.sun.management.HotSpotDiagnosticMXBean
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.ResourceMonitoring
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.moshi.Adapters
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import edu.illinois.cs.cs125.questioner.lib.warm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.logging.toLogString
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryNotificationInfo
import java.lang.management.MemoryType
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import kotlin.math.floor
import kotlin.math.pow
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds
import edu.illinois.cs.cs125.jeed.core.moshi.Adapters as JeedAdapters
import edu.illinois.cs.cs125.jeed.core.warm as warmJeed
import kotlin.math.round as kotlinRound

internal val logger = KotlinLogging.logger {}

internal val questionCacheSize = (System.getenv("QUESTIONER_QUESTION_CACHE_SIZE")?.toLong() ?: 16L) + 1
private val warmQuestion = System.getenv("QUESTIONER_WARM_QUESTION") ?: "hello-world"

private val runtime: Runtime = Runtime.getRuntime()

private fun totalMemoryMB() = (runtime.totalMemory().toFloat() / 1024.0 / 1024.0).toInt()
private fun freeMemoryMB() = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
private fun usedMemoryMB() = (totalMemoryMB() - freeMemoryMB()).coerceAtLeast(0)
private fun memoryPercent() = (usedMemoryMB().toFloat() / totalMemoryMB().toFloat() * 100.0).toInt()
private fun printMemory() = "Heap ${memoryPercent()}% (${usedMemoryMB()} / ${totalMemoryMB()})"

private fun Double.round(decimals: Int) = 10.0.pow(decimals)
    .let { multiplier -> kotlinRound(this * multiplier) / multiplier }

@Suppress("LongMethod")
fun Application.questioner() {
    val callStartTime = AttributeKey<Long>("CallStartTime")
    val counter = AtomicInteger()

    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(callStartTime, Instant.now().toEpochMilli())
    }

    install(CallLogging) {
        filter { call ->
            call.request.path() != "/version" && !(call.request.httpMethod.value == "GET" && call.request.path() == "/")
        }
        format { call ->
            val startTime = call.attributes.getOrNull(callStartTime)
            "${call.response.status()}: ${call.request.toLogString()} ${
                if (startTime != null) {
                    Instant.now().toEpochMilli() - startTime
                } else {
                    ""
                }
            }"
        }
    }
    install(ContentNegotiation) {
        moshi {
            Adapters.forEach { adapter -> add(adapter) }
            JeedAdapters.forEach { adapter -> add(adapter) }
        }
    }
    routing {
        get("/") {
            call.respond(Status())
        }
        get("/version") {
            call.respond(VERSION)
        }
        post("/") {
            val submitted = Instant.now()
            val runCount = counter.incrementAndGet()

            val submission = call.receive<Submission>()
            val question = submission.getQuestion() ?: return@post call.respond(HttpStatusCode.NotFound)

            val validated = when (submission.type) {
                Submission.SubmissionType.SOLVE -> question.validated
                Submission.SubmissionType.TESTTESTING -> question.testTestingValidated && question.validationResults?.canTestTest == true
            }
            if (!validated) {
                return@post call.respond(HttpStatusCode.BadRequest)
            }

            @Suppress("TooGenericExceptionCaught")
            try {
                val startMemory = freeMemoryMB()
                val response = submission.test(question)
                call.respond(response)
                val endMemory = freeMemoryMB()
                logger.trace(
                    "$runCount: ${question.fullPath}: $startMemory -> $endMemory (${
                        Instant.now().toEpochMilli() - submitted.toEpochMilli()
                    }, ${response.duration})",
                )
                logger.trace("Cache hit rate: ${questionCache.stats().hitRate()} (Size $questionCacheSize)")
            } catch (e: StackOverflowError) {
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            } catch (e: Error) {
                e.printStackTrace()
                logger.warn { submission }
                logger.error(e.toString())
                // Firm shutdown
                Runtime.getRuntime().halt(-1)
            } catch (e: Throwable) {
                e.printStackTrace()
                logger.warn(e.toString())
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}

suspend fun doWarm() {
    val question = getQuestionByPath(warmQuestion)?.apply {
        check(published.languages.contains(Language.java)) { "Warm question should support Java" }
        check(published.languages.contains(Language.kotlin)) { "Warm question should support Kotlin" }
    } ?: error("Warm question should exist")
    warm(question)
}

@OptIn(DelicateCoroutinesApi::class, ObsoleteCoroutinesApi::class)
fun main(@Suppress("unused") unused: Array<String>): Unit = runBlocking {
    ResourceMonitoring.ensureAgentActivated()

    check(System.getenv("QUESTIONER_MAX_CONCURRENCY") != null) {
        "Please set the QUESTIONER_MAX_CONCURRENCY environment variable"
    }

    check(System.getenv("QUESTIONER_TEST_TIMEOUT_MS") != null) {
        "Please set the QUESTIONER_TEST_TIMEOUT_MS environment variable"
    }

    check(System.getenv("QUESTIONER_TESTTEST_TIMEOUT_MS") != null) {
        "Please set the QUESTIONER_TESTTEST_TIMEOUT_MS environment variable"
    }

    (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(logger.name).level =
        System.getenv("LOG_LEVEL")?.let { Level.toLevel(it) } ?: Level.INFO

    logger.info { Status().toJson() }

    val memoryLimitThreshold = System.getenv("MEMORY_LIMIT_THRESHOLD")?.toDoubleOrNull() ?: 0.8
    val memoryLimitAction = System.getenv("MEMORY_LIMIT_ACTION") ?: "ignore"

    ManagementFactory.getMemoryPoolMXBeans()
        .find { bean -> bean.type == MemoryType.HEAP && bean.isUsageThresholdSupported }
        ?.also { bean ->
            val threshold = floor(bean.usage.max * memoryLimitThreshold).toLong()
            logger.trace("Setting memory collection threshold to $threshold")
            bean.collectionUsageThreshold = threshold
            val listener = NotificationListener { notification, _ ->
                if (notification.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED) {
                    if (memoryLimitAction == "reboot") {
                        logger.error("Memory threshold exceeded, rebooting as requested")
                        exitProcess(-1)
                    } else if (memoryLimitAction == "warn") {
                        logger.warn("Memory threshold exceeded")
                    }
                }
            }
            (ManagementFactory.getMemoryMXBean() as NotificationEmitter).addNotificationListener(listener, null, null)
        } ?: logger.warn("Memory management interface not found")

    logger.info("Warming Jeed")
    try {
        warmJeed(2, failLint = false)
    } catch (e: Exception) {
        logger.error("Warming Jeed failed: $e")
        exitProcess(-1)
    }

    (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(logger.name).also { logLevel ->
        logger.info("Starting questioner server (log level $logLevel)")
    }

    val heartbeatInterval = System.getenv("HEARTBEAT_INTERVAL_SEC")?.toLong() ?: 300L
    flow {
        for (i in generateSequence(0) { it + 1 }) {
            emit(i)
            delay(heartbeatInterval.seconds)
        }
    }.map { index ->
        val warmTime = measureTimeMillis {
            try {
                doWarm()
            } catch (e: Exception) {
                logger.error("Questioner heartbeat failed: $e. Restarting.")
                exitProcess(-1)
            }
            System.getenv("DUMP_AFTER_HEARTBEAT")?.also {
                val heapDumpName = """questioner_${index.toString().padStart(6)}_${
                    ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                }.hprof"""
                logger.debug("Dumping heap")
                ManagementFactory.newPlatformMXBeanProxy(
                    ManagementFactory.getPlatformMBeanServer(),
                    "com.sun.management:type=HotSpotDiagnostic",
                    HotSpotDiagnosticMXBean::class.java,
                ).dumpHeap(heapDumpName, false)
            }
        }
        logger.trace("Questioner heartbeat $index completed in ${(warmTime / 1000.0).round(1)}s. ${printMemory()}. Cache ${questionCache.estimatedSize()} / $questionCacheSize.")
    }.launchIn(GlobalScope)

    embeddedServer(Netty, port = 8888, module = Application::questioner).start(wait = true)
}
