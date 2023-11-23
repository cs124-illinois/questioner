package edu.illinois.cs.cs125.questioner.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import edu.illinois.cs.cs125.jeed.core.useCompilationCache
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.ResourceMonitoring
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.TestTestResults
import edu.illinois.cs.cs125.questioner.lib.moshi.Adapters
import edu.illinois.cs.cs125.questioner.lib.stumpers.Candidate
import edu.illinois.cs.cs125.questioner.lib.stumpers.Solution
import edu.illinois.cs.cs125.questioner.lib.stumpers.clean
import edu.illinois.cs.cs125.questioner.lib.stumpers.createInsertionIndices
import edu.illinois.cs.cs125.questioner.lib.stumpers.validated
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
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import com.github.benmanes.caffeine.cache.stats.CacheStats as CaffeineCacheStats
import edu.illinois.cs.cs125.jeed.core.moshi.Adapters as JeedAdapters
import edu.illinois.cs.cs125.jeed.core.warm as warmJeed

private val moshi = Moshi.Builder().apply {
    JeedAdapters.forEach { add(it) }
    Adapters.forEach { add(it) }
}.build()
private val logger = KotlinLogging.logger {}

private val trustAllCerts = object : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate>? {
        return null
    }

    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
}

private val sc = SSLContext.getInstance("SSL").apply {
    init(null, arrayOf(trustAllCerts), SecureRandom())
}

private val questionerCollection: MongoCollection<BsonDocument> = run {
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

private val stumperSolutionCollection: MongoCollection<BsonDocument> = run {
    require(System.getenv("STUMPERDB") != null) { "STUMPERDB environment variable not set" }
    val keystore = System.getenv("KEYSTORE_FILE")
    if (keystore != null) {
        require(System.getenv("KEYSTORE_PASSWORD") != null) { "Must set KEYSTORE_PASSWORD" }
        System.setProperty("javax.net.ssl.trustStore", keystore)
        System.setProperty("javax.net.ssl.trustStorePassword", System.getenv("KEYSTORE_PASSWORD"))
    }
    val mongoUri = MongoClientURI(System.getenv("STUMPERDB")!!, MongoClientOptions.builder().sslContext(sc))
    val database = mongoUri.database ?: error("STUMPERDB must specify database to use")
    MongoClient(mongoUri).getDatabase(database).getCollection("solutions", BsonDocument::class.java)
}

@JsonClass(generateAdapter = true)
data class SkinnyQuestion(val published: Question.Published, val metadata: Question.Metadata) {
    val path = "${published.author}/${published.path}/${published.version}/${metadata.contentHash}"
}

data class QuestionPath(val path: String, val version: String, val author: String) {
    companion object {
        fun fromSubmission(submission: Submission) =
            QuestionPath(submission.path, submission.version!!, submission.author!!)

        fun fromTestSubmission(submission: TestSubmission) =
            QuestionPath(submission.path, submission.version!!, submission.author!!)
    }
}

@JsonClass(generateAdapter = true)
data class CacheStats(val hits: Long, val misses: Long) {
    constructor(caffeineStats: CaffeineCacheStats) : this(caffeineStats.hitCount(), caffeineStats.missCount())
}

val questionCacheSize = System.getenv("QUESTIONER_QUESTION_CACHE_SIZE")?.toLong() ?: 16L
val questionCache: Cache<String, Question> = Caffeine.newBuilder().maximumSize(questionCacheSize).recordStats().build()

object Questions {
    private fun questionFromDocument(document: BsonDocument, id: String): Question? {
        val json = document.toJson()
        return try {
            moshi.adapter(SkinnyQuestion::class.java).fromJson(json)!!.let { skinnyQuestion ->
                questionCache.get(skinnyQuestion.path) {
                    logger.debug { "Question cache miss for ${skinnyQuestion.path}" }
                    try {
                        moshi.adapter(Question::class.java).fromJson(json)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn { "Couldn't load question $id, which might use an old schema: $e" }
            null
        }
    }

    fun getQuestion(path: String) = questionerCollection.find(
        Filters.and(Filters.eq("published.path", path), Filters.eq("latest", true)),
    ).sort(Sorts.descending("updated")).let {
        @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
        if (it.count() == 0) {
            return null
        }
        check(it.count() == 1) { "Found multiple path-only matches" }
        questionFromDocument(it.first()!!, path)
    }

    private fun getQuestionByAuthor(path: String, author: String) = questionerCollection.find(
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
        questionFromDocument(it.first()!!, "$path/$author")
    }

    private fun getQuestionByPath(path: QuestionPath) = questionerCollection.find(
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
        questionFromDocument(it.first()!!, "$path")
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

    suspend fun test(submission: Submission, question: Question): TestResults {
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
    suspend fun testtest(submission: TestSubmission, question: Question, settings: Question.TestTestingSettings): TestTestResults {
        val start = Instant.now().toEpochMilli()
        logger.trace { "Test tests for ${question.name}" }
        return question.testTests(
            submission.contents,
            language = submission.language,
            settings = settings,
        ).also {
            logger.trace { "Tests tested for ${question.name} in ${Instant.now().toEpochMilli() - start}" }
        }
    }
}

@JsonClass(generateAdapter = true)
data class Submission(
    val path: String,
    val contents: String,
    val language: Language,
    val disableLineCountLimit: Boolean = false,
    val disableAllocationLimit: Boolean = false,
    val version: String?,
    val author: String?,
    val email: String?,
    val originalID: String?,
) {
    constructor(contents: String, language: Language, question: Question) :
        this(
            question.published.path,
            contents,
            language,
            false,
            false,
            question.published.version,
            question.published.author,
            null,
            null,
        )
}

@JsonClass(generateAdapter = true)
data class TestSubmission(
    val path: String,
    val contents: String,
    val language: Language,
    val version: String?,
    val author: String?,
    val limit: Int = Int.MAX_VALUE,
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

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs124.questioner.lib.version"))
}.getProperty("version")

@JsonClass(generateAdapter = true)
data class Status(
    val started: Instant = serverStarted,
    val version: String = VERSION,
    val useJeedCache: Boolean = useCompilationCache,
)

@JsonClass(generateAdapter = true)
data class ServerResponse(val results: TestResults, val canCache: Boolean, val cacheStats: CacheStats)

@JsonClass(generateAdapter = true)
data class ServerTestResponse(val results: TestTestResults, val canCache: Boolean, val cacheStats: CacheStats)

val runtime: Runtime = Runtime.getRuntime()
val counter = AtomicInteger()

val CALL_START_TIME = AttributeKey<Long>("CallStartTime")

suspend fun addStumperSolution(
    submitted: Instant,
    submission: Submission,
    testResults: TestResults,
    question: Question,
) {
    if (!testResults.validated() || submission.originalID == null) {
        return
    }
    val candidate = Candidate(
        submitted,
        submission.contents,
        submission.originalID,
        question,
        submission.language,
    )
    if (candidate.exists(stumperSolutionCollection)) {
        return
    }
    val solution = try {
        candidate.clean().copy(
            valid = true,
            validation = Solution.Validation(
                submitted,
                question.published.version,
                question.metadata.contentHash,
                VERSION,
            ),
        )
    } catch (e: Exception) {
        logger.warn { e }
        return
    }
    if (solution.exists(stumperSolutionCollection)) {
        return
    }
    try {
        solution.save(stumperSolutionCollection)
    } catch (e: Exception) {
        logger.warn { e }
    }
}

@Suppress("LongMethod")
fun Application.questioner() {
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(CALL_START_TIME, Instant.now().toEpochMilli())
    }
    install(CallLogging) {
        filter { call ->
            call.request.path() != "/version" && !(call.request.httpMethod.value == "GET" && call.request.path() == "/")
        }
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
        get("/version") {
            call.respond(VERSION)
        }
        post("/") {
            val submitted = Instant.now()
            val runCount = counter.incrementAndGet()

            val submission = call.receive<Submission>()

            val question = Questions.load(submission) ?: return@post call.respond(HttpStatusCode.NotFound)
            if (!question.validated) {
                return@post call.respond(HttpStatusCode.BadRequest)
            }
            @Suppress("TooGenericExceptionCaught")
            try {
                val startMemory = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
                val testResults = Questions.test(submission, question)
                call.respond(
                    ServerResponse(
                        testResults,
                        !(testResults.timeout && !testResults.lineCountTimeout),
                        CacheStats(questionCache.stats()),
                    ),
                )
                val endMemory = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
                logger.debug {
                    "$runCount: ${submission.path}: $startMemory -> $endMemory (${
                        Instant.now().toEpochMilli() - submitted.toEpochMilli()
                    })"
                }
                logger.debug { "Cache hit rate: ${questionCache.stats().hitRate()} (Size $questionCacheSize)" }
                try {
                    addStumperSolution(submitted, submission, testResults, question)
                } catch (e: Exception) {
                    logger.warn { e }
                }
            } catch (e: StackOverflowError) {
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            } catch (e: Error) {
                e.printStackTrace()
                logger.debug { submission }
                logger.error { e.toString() }
                // Firm shutdown
                Runtime.getRuntime().halt(-1)
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
            val runCount = counter.incrementAndGet()

            val submission = call.receive<TestSubmission>()
            val question = Questions.loadTest(submission) ?: return@post call.respond(HttpStatusCode.NotFound)
            if (!question.canTestTest) {
                return@post call.respond(HttpStatusCode.NotFound)
            }
            if (!question.validated || !question.testTestingValidated) {
                logger.warn { "Question not validated or not validated for test testing" }
                return@post call.respond(HttpStatusCode.BadRequest)
            }

            @Suppress("TooGenericExceptionCaught")
            try {
                val startMemory = (runtime.freeMemory().toFloat() / 1024.0 / 1024.0).toInt()
                val testSettings = Question.TestTestingSettings(limit = submission.limit)
                call.respond(
                    ServerTestResponse(
                        Questions.testtest(submission, question, testSettings),
                        false,
                        CacheStats(questionCache.stats()),
                    ),
                )
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
                // Firm shutdown
                Runtime.getRuntime().halt(-1)
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
    }
}

suspend fun warm() {
    val question = Questions.getQuestion("hello-world")
    check(question != null) { "Warm question should exist" }
    check(question.published.languages.contains(Language.java)) {
        "Warm question should support Java"
    }
    check(question.published.languages.contains(Language.kotlin)) {
        "Warm question should support Kotlin"
    }
    val java = Submission("""System.out.println("Hello, world!);""", Language.java, question)
    Questions.test(java, question)
    val kotlin = Submission("""println("Hello, world!)""", Language.kotlin, question)
    Questions.test(kotlin, question)
}

fun main(): Unit = runBlocking {
    ResourceMonitoring.ensureAgentActivated()

    if (System.getenv("LOG_LEVEL_DEBUG") != null) {
        (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(logger.name).level = Level.DEBUG
        logger.debug { "Enabling debug logging" }
    }

    val memoryLimitThreshold = System.getenv("MEMORY_LIMIT_THRESHOLD")?.toDoubleOrNull() ?: 0.8
    ManagementFactory.getMemoryPoolMXBeans().find {
        it.type == MemoryType.HEAP && it.isUsageThresholdSupported
    }?.also {
        val threshold = floor(it.usage.max * memoryLimitThreshold).toLong()
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

    CoroutineScope(Dispatchers.IO).launch {
        stumperSolutionCollection.createInsertionIndices()
    }

    logger.info { "Warming Jeed" }
    warmJeed(2, failLint = false)

    logger.info { "Warming Questioner" }
    warm()

    logger.info { "Starting server" }
    embeddedServer(Netty, port = 8888, module = Application::questioner).start(wait = true)
}
