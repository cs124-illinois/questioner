package edu.illinois.cs.cs125.questioner.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.ryanharter.ktor.moshi.moshi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.sun.management.HotSpotDiagnosticMXBean
import edu.illinois.cs.cs125.jeed.core.warm
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.ResourceMonitoring
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.TestTestResults
import edu.illinois.cs.cs125.questioner.lib.moshi.Adapters
import edu.illinois.cs.cs125.questioner.lib.test
import edu.illinois.cs.cs125.questioner.lib.testTests
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.logging.toLogString
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.bson.BsonDocument
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryNotificationInfo
import java.lang.management.MemoryType
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.collections.forEach
import kotlin.math.floor
import kotlin.system.exitProcess
import edu.illinois.cs.cs125.jeed.core.moshi.Adapters as JeedAdapters

private val moshi = Moshi.Builder().apply {
    JeedAdapters.forEach { add(it) }
    Adapters.forEach { add(it) }
}.build()
private val logger = KotlinLogging.logger {}

private val collection: MongoCollection<BsonDocument> = run {
    val trustAllCerts = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate>? {
            return null
        }

        override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
        override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
    }

    val sc = SSLContext.getInstance("SSL").apply {
        init(null, arrayOf(trustAllCerts), SecureRandom())
    }

    require(System.getenv("MONGODB") != null) { "MONGODB environment variable not set" }
    val keystore = System.getenv("KEYSTORE_FILE")
    if (keystore != null) {
        require(System.getenv("KEYSTORE_PASSWORD") != null) { "Must set KEYSTORE_PASSWORD" }
        System.setProperty("javax.net.ssl.trustStore", keystore)
        System.setProperty("javax.net.ssl.trustStorePassword", System.getenv("KEYSTORE_PASSWORD"))
    }
    val collection = System.getenv("MONGODB_COLLECTION") ?: "questioner"
    val mongoUri = MongoClientURI(System.getenv("MONGODB")!!, MongoClientOptions.builder().sslContext(sc))
    val database = mongoUri.database ?: error("MONGODB must specify database to use")
    MongoClient(mongoUri).getDatabase(database).getCollection(collection, BsonDocument::class.java)
}

data class QuestionPath(val path: String, val version: String, val author: String) {
    companion object {
        fun fromSubmission(submission: Submission) =
            QuestionPath(submission.path, submission.version!!, submission.author!!)
        fun fromTestSubmission(submission: TestSubmission) =
            QuestionPath(submission.path, submission.version!!, submission.author!!)
    }
}

object Questions {
    private fun getQuestion(path: String) = collection.find(
        Filters.and(Filters.eq("published.path", path), Filters.eq("latest", true)),
    ).sort(Sorts.descending("updated")).let {
        @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
        if (it.count() == 0) {
            return null
        }
        check(it.count() == 1) { "Found multiple path-only matches" }
        try {
            moshi.adapter(Question::class.java).fromJson(it.first()!!.toJson())
        } catch (e: Exception) {
            logger.warn { "Couldn't load question $path, which might use an old schema: $e" }
            null
        }
    }

    private fun getQuestionByAuthor(path: String, author: String) = collection.find(
        Filters.and(
            Filters.eq("published.path", path),
            Filters.eq("published.author", author),
            Filters.eq("latest", true),
        ),
    ).sort(Sorts.descending("updated")).let {
        @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
        if (it.count() == 0) {
            return null
        }
        check(it.count() == 1) { "Found multiple path and author matches" }
        try {
            moshi.adapter(Question::class.java).fromJson(it.first()!!.toJson())
        } catch (e: Exception) {
            logger.warn { "Couldn't load question $path, which might use an old schema: $e" }
            null
        }
    }
    private fun getQuestionByPath(path: QuestionPath) = collection.find(
        Filters.and(
            Filters.eq("published.path", path.path),
            Filters.eq("published.version", path.version),
            Filters.eq("published.author", path.author),
            Filters.eq("latest", true),
        ),
    ).sort(Sorts.descending("updated")).let {
        @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
        if (it.count() == 0) {
            return null
        }
        check(it.count() == 1) { "Found multiple full-path matches" }
        try {
            moshi.adapter(Question::class.java).fromJson(it.first()!!.toJson())
        } catch (e: Exception) {
            logger.warn { "Couldn't load question $path, which might use an old schema: $e" }
            null
        }
    }

    fun load(path: String): Question? {
        return getQuestion(path)
    }

    fun load(submission: Submission): Question? {
        return if (submission.version != null && submission.author != null) {
            getQuestionByPath(QuestionPath.fromSubmission(submission))
        } else if (submission.author != null) {
            getQuestionByAuthor(submission.path, submission.author)
        } else {
            getQuestion(submission.path)
        }
    }

    suspend fun test(submission: Submission): TestResults {
        val question = load(submission) ?: error("No question ${submission.path}")
        check(question.validated) { "Question ${submission.path} is not validated" }
        val start = Instant.now().toEpochMilli()
        val timeout = question.testingSettings!!.timeout * (System.getenv("TIMEOUT_MULTIPLIER")?.toInt() ?: 1)
        val settings = question.testingSettings!!.copy(
            timeout = timeout,
            disableLineCountLimit = submission.disableLineCountLimit,
            disableAllocationLimit = submission.disableAllocationLimit,
        )
        logger.trace { "Testing ${question.name} with settings $settings" }
        return question.test(
            submission.contents,
            language = submission.language,
            settings = settings,
        ).also {
            logger.trace { "Tested ${question.name} in ${Instant.now().toEpochMilli() - start}" }
        }
    }

    fun loadTest(submission: TestSubmission): Question? {
        return if (submission.version != null && submission.author != null) {
            getQuestionByPath(QuestionPath.fromTestSubmission(submission))
        } else if (submission.author != null) {
            getQuestionByAuthor(submission.path, submission.author)
        } else {
            getQuestion(submission.path)
        }
    }

    @Suppress("SpellCheckingInspection")
    suspend fun testtest(submission: TestSubmission): TestTestResults {
        val question = loadTest(submission) ?: error("No question ${submission.path}")
        check(question.validated) { "Question ${submission.path} is not validated" }
        val start = Instant.now().toEpochMilli()
        logger.trace { "Test tests for ${question.name}" }
        return question.testTests(
            submission.contents,
            language = submission.language,
        ).also {
            logger.trace { "Tests tested for ${question.name} in ${Instant.now().toEpochMilli() - start}" }
        }
    }
}

@JsonClass(generateAdapter = true)
data class Submission(
    val path: String,
    val contents: String,
    val language: Question.Language,
    val disableLineCountLimit: Boolean = false,
    val disableAllocationLimit: Boolean = false,
    val version: String?,
    val author: String?,
)

@JsonClass(generateAdapter = true)
data class TestSubmission(
    val path: String,
    val contents: String,
    val language: Question.Language,
    val version: String?,
    val author: String?,
)

@JsonClass(generateAdapter = true)
data class QuestionDescription(
    val path: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val packageName: String,
    val starter: String?,
)

private val serverStarted = Instant.now()

val versionString = run {
    @Suppress("TooGenericExceptionCaught")
    try {
        val versionFile = object {}::class.java.getResource("/edu.illinois.cs.cs125.questioner.server.version")
        Properties().also { it.load(versionFile!!.openStream()) }["version"] as String
    } catch (e: Exception) {
        println(e)
        "unspecified"
    }
}

@JsonClass(generateAdapter = true)
data class Status(
    val started: Instant = serverStarted,
    val version: String = versionString,
)

@JsonClass(generateAdapter = true)
data class ServerResponse(val results: TestResults)

@JsonClass(generateAdapter = true)
data class ServerTestResponse(val results: TestTestResults)

val runtime: Runtime = Runtime.getRuntime()
val counter = AtomicInteger()

val CALL_START_TIME = AttributeKey<Long>("CallStartTime")

@Suppress("LongMethod")
fun Application.questioner() {
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(CALL_START_TIME, Instant.now().toEpochMilli())
    }
    install(CallLogging) {
        format { call ->
            val startTime = call.attributes.getOrNull(CALL_START_TIME)
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
            Adapters.forEach { this.add(it) }
            JeedAdapters.forEach { this.add(it) }
        }
    }
    routing {
        get("/") {
            call.respond(Status())
        }
        post("/") {
            val start = Instant.now().toEpochMilli()
            val submission = call.receive<Submission>()
            Questions.load(submission) ?: return@post call.respond(HttpStatusCode.NotFound)
            val runCount = counter.incrementAndGet()
            @Suppress("TooGenericExceptionCaught")
            try {
                val startMemory = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
                call.respond(ServerResponse(Questions.test(submission)))
                val endMemory = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
                logger.debug {
                    "$runCount: ${submission.path}: $startMemory -> $endMemory (${
                        Instant.now().toEpochMilli() - start
                    })"
                }
            } catch (e: StackOverflowError) {
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            } catch (e: Error) {
                e.printStackTrace()
                logger.debug { submission }
                logger.error { e.toString() }
                exitProcess(-1)
            } catch (e: Throwable) {
                e.printStackTrace()
                logger.warn { e.toString() }
                call.respond(HttpStatusCode.BadRequest)
            } finally {
                System.getenv("DUMP_AT_SUBMISSION")?.toInt()?.also {
                    if (it == runCount) {
                        logger.debug { "Dumping heap" }
                        ManagementFactory.newPlatformMXBeanProxy(
                            ManagementFactory.getPlatformMBeanServer(),
                            "com.sun.management:type=HotSpotDiagnostic",
                            HotSpotDiagnosticMXBean::class.java,
                        ).dumpHeap("questioner.hprof", false)
                    }
                }
            }
        }
        post("/tests") {
            val start = Instant.now().toEpochMilli()
            val submission = call.receive<TestSubmission>()
            Questions.loadTest(submission) ?: return@post call.respond(HttpStatusCode.NotFound)
            val runCount = counter.incrementAndGet()
            @Suppress("TooGenericExceptionCaught")
            try {
                val startMemory = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
                call.respond(ServerTestResponse(Questions.testtest(submission)))
                val endMemory = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
                logger.debug {
                    "$runCount: test/${submission.path}: $startMemory -> $endMemory (${
                        Instant.now().toEpochMilli() - start
                    })"
                }
            } catch (e: StackOverflowError) {
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            } catch (e: Error) {
                e.printStackTrace()
                logger.debug { submission }
                logger.error { e.toString() }
                exitProcess(-1)
            } catch (e: Throwable) {
                e.printStackTrace()
                logger.warn { e.toString() }
                call.respond(HttpStatusCode.BadRequest)
            } finally {
                System.getenv("DUMP_AT_SUBMISSION")?.toInt()?.also {
                    if (it == runCount) {
                        logger.debug { "Dumping heap" }
                        ManagementFactory.newPlatformMXBeanProxy(
                            ManagementFactory.getPlatformMBeanServer(),
                            "com.sun.management:type=HotSpotDiagnostic",
                            HotSpotDiagnosticMXBean::class.java,
                        ).dumpHeap("questioner.hprof", false)
                    }
                }
            }
        }
        get("/question/java/{path}") {
            val path = call.parameters["path"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val question = Questions.load(path) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(
                QuestionDescription(
                    path,
                    question.name,
                    question.metadata.version,
                    question.metadata.javaDescription,
                    question.metadata.author,
                    question.metadata.packageName,
                    question.detemplatedJavaStarter,
                ),
            )
        }
        get("/question/kotlin/{path}") {
            val path = call.parameters["path"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val question = Questions.load(path) ?: return@get call.respond(HttpStatusCode.NotFound)
            if (!question.hasKotlin) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            call.respond(
                QuestionDescription(
                    path,
                    question.name,
                    question.metadata.version,
                    question.metadata.kotlinDescription!!,
                    question.metadata.author,
                    question.metadata.packageName,
                    starter = question.detemplatedKotlinStarter,
                ),
            )
        }
    }
}

fun main() {
    ResourceMonitoring.ensureAgentActivated()

    if (System.getenv("LOG_LEVEL_DEBUG") != null) {
        (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(logger.name).level = Level.DEBUG
        logger.debug { "Enabling debug logging" }
    }

    ManagementFactory.getMemoryPoolMXBeans().find {
        it.type == MemoryType.HEAP && it.isUsageThresholdSupported
    }?.also {
        val threshold = floor(it.usage.max * 0.8).toLong()
        logger.debug { "Setting memory collection threshold to $threshold" }
        it.collectionUsageThreshold = threshold
        val listener = NotificationListener { notification, _ ->
            if (notification.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED) {
                logger.error { "Memory threshold exceeded" }
                exitProcess(-1)
            }
        }
        (ManagementFactory.getMemoryMXBean() as NotificationEmitter).addNotificationListener(listener, null, null)
    } ?: logger.warn { "Memory management interface not found" }

    logger.debug { Status() }
    CoroutineScope(Dispatchers.IO).launch { warm(2, failLint = false) }
    embeddedServer(Netty, port = 8888, module = Application::questioner).start(wait = true)
}
