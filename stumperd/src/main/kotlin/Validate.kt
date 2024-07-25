package edu.illinois.cs.cs124.stumperd

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.test

private val questionCacheSize = System.getenv("QUESTIONER_QUESTION_CACHE_SIZE").toLong()

val warmedQuestionHash: Cache<String, Question> = Caffeine.newBuilder().maximumSize(questionCacheSize).build()

suspend fun Stumper.validate() = doStep(Stumper.Steps.VALIDATE) {
    val question = warmedQuestionHash.get(question.published.contentHash) { question }
        .also { question ->
            question.warm()
        }
    val results = question.test(cleanedContents, language)
    check(results.complete.partial?.passedSteps?.quality == true) {
        "Stumper did not pass validation"
    }
}
