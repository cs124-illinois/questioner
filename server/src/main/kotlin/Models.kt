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
import edu.illinois.cs.cs125.questioner.lib.questionerMaxConcurrency
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import edu.illinois.cs.cs125.questioner.lib.test
import edu.illinois.cs.cs125.questioner.lib.testTests
import java.time.Instant

internal fun Question.toSubmission(type: Submission.SubmissionType, language: Language, contents: String) =
    Submission(type, published.contentHash, language, contents)

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

@JsonClass(generateAdapter = true)
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
