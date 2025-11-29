package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.jeed.core.compilationCacheSizeMB
import edu.illinois.cs.cs125.jeed.core.serializers.InstantSerializer
import edu.illinois.cs.cs125.jeed.core.useCompilationCache
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.TestTestResults
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import edu.illinois.cs.cs125.questioner.lib.test
import edu.illinois.cs.cs125.questioner.lib.testTests
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Instant

internal suspend fun Submission.test(question: Question): ServerResponse {
    val start = Instant.now()
    return when (type) {
        Submission.SubmissionType.SOLVE -> {
            val solveResults = question.test(contents, language)
            ServerResponse(
                type = Submission.SubmissionType.SOLVE,
                solveResults = solveResults,
                canCache = solveResults.canCache,
            )
        }

        Submission.SubmissionType.TESTTESTING -> {
            val testTestingResults = question.testTests(contents, language, testTestingSettings)
            ServerResponse(
                type = Submission.SubmissionType.TESTTESTING,
                testTestingResults = testTestingResults,
                canCache = testTestingResults.canCache,
            )
        }
    }.copy(cacheStats = getStats(), duration = Instant.now().toEpochMilli() - start.toEpochMilli())
}

@Serializable
internal data class ServerResponse(
    val type: Submission.SubmissionType,
    val solveResults: TestResults? = null,
    val testTestingResults: TestTestResults? = null,
    val canCache: Boolean,
    val cacheStats: CacheStats? = null,
    val duration: Long = 0,
    val version: String = VERSION,
)

private val serverStarted = Instant.now()

@Serializable
internal data class Status(
    val name: String = "questioner",
    @Serializable(with = InstantSerializer::class)
    val started: Instant = serverStarted,
    val version: String = VERSION,
    val useJeedCache: Boolean = useCompilationCache,
    val resources: Resources = Resources(),
    val settings: Settings = Settings(),
) {
    @Serializable
    data class Settings(
        val useJeedCache: Boolean = useCompilationCache,
        val jeedCacheSize: Long = compilationCacheSizeMB,
        val cacheSize: Long = questionCacheSize,
        val maxConcurrency: Int = System.getenv("QUESTIONER_MAX_CONCURRENCY").toInt(),
        val testTimeout: Int = System.getenv("QUESTIONER_TEST_TIMEOUT_MS").toInt(),
        val testTestTimeout: Int = System.getenv("QUESTIONER_TESTTEST_TIMEOUT_MS").toInt(),
    )

    @Serializable
    data class Resources(
        val processors: Int = Runtime.getRuntime().availableProcessors(),
        val totalMemory: Long = Runtime.getRuntime().totalMemory() / 1024 / 1024,
        var freeMemory: Long = Runtime.getRuntime().freeMemory() / 1024 / 1024,
    )

    fun toJson(): String = json.encodeToString(this)
}
