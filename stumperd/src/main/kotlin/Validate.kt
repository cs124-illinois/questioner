package edu.illinois.cs.cs124.stumperd.server

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.test

class ValidationFailure(cause: Throwable) : StumperFailure(Steps.VALIDATE, cause)

private val questionCacheSize = System.getenv("QUESTIONER_QUESTION_CACHE_SIZE").toLong()

private val questionCache: Cache<String, Question> = Caffeine.newBuilder().maximumSize(questionCacheSize).build()

typealias Validated = Rededuplicated

suspend fun Rededuplicated.validate(): Validated = try {
    val question = questionCache.get(identified.question.published.contentHash) { identified.question }
        .also { question ->
            question.warm()
        }
    val results = question.test(cleanedContents, identified.submission.language)
    check(results.complete.partial?.passedSteps?.quality == true) {
        "Stumper did not pass validation"
    }
    this
} catch (e: NoClassDefFoundError) {
    // TODO: Remove workaround for broken question
    throw ValidationFailure(e)
} catch (e: Exception) {
    throw ValidationFailure(e)
}
