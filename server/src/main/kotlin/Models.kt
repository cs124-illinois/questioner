package edu.illinois.cs.cs125.questioner.server

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.compilationCacheSizeMB
import edu.illinois.cs.cs125.jeed.core.useCompilationCache
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.TestTestResults
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import edu.illinois.cs.cs125.questioner.lib.test
import edu.illinois.cs.cs125.questioner.lib.testTests
import java.time.Instant

// TODO: Add stumper stuff
@JsonClass(generateAdapter = true)
internal data class Submission(
    val type: SubmissionType,
    val contentHash: String,
    val language: Language,
    val contents: String,
    val originalID: String? = null,
) {
    enum class SubmissionType { SOLVE, TESTTESTING }
}

internal fun Question.toSubmission(type: Submission.SubmissionType, language: Language, contents: String) =
    Submission(type, published.contentHash, language, contents)

internal suspend fun Submission.test(question: Question): ServerResponse {
    // FIXME
    // val timeout = question.testingSettings!!.timeout * (System.getenv("TIMEOUT_MULTIPLIER")?.toInt() ?: 1)
    val start = Instant.now()
    return when (type) {
        Submission.SubmissionType.SOLVE -> {
            val solveResults = question.test(contents, language)
            ServerResponse(solveResults = solveResults, canCache = solveResults.canCache)
        }

        Submission.SubmissionType.TESTTESTING -> {
            val testTestingResults = question.testTests(contents, language)
            ServerResponse(testTestingResults = testTestingResults, canCache = testTestingResults.canCache)
        }
    }.copy(cacheStats = getStats(), duration = Instant.now().toEpochMilli() - start.toEpochMilli())
}

@JsonClass(generateAdapter = true)
internal data class ServerResponse(
    val solveResults: TestResults? = null,
    val testTestingResults: TestTestResults? = null,
    val canCache: Boolean,
    val cacheStats: CacheStats? = null,
    val duration: Long = 0,
)

private val serverStarted = Instant.now()

@JsonClass(generateAdapter = true)
internal data class Status(
    val started: Instant = serverStarted,
    val version: String = VERSION,
    val useJeedCache: Boolean = useCompilationCache,
    val resources: Resources = Resources(),
    val settings: Settings = Settings(),
) {
    @JsonClass(generateAdapter = true)
    data class Settings(
        val useJeedCache: Boolean = useCompilationCache,
        val jeedCacheSize: Long = compilationCacheSizeMB,
        val cacheSize: Long = questionCacheSize,
        val maxConcurrency: Int = questionerMaxConcurrency,
    )

    @JsonClass(generateAdapter = true)
    data class Resources(
        val processors: Int = Runtime.getRuntime().availableProcessors(),
        val totalMemory: Long = Runtime.getRuntime().totalMemory() / 1024 / 1024,
        var freeMemory: Long = Runtime.getRuntime().freeMemory() / 1024 / 1024,
    )

    fun toJson(): String = moshi.adapter(Status::class.java).indent("  ").toJson(this)
}
