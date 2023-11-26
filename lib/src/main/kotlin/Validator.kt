package edu.illinois.cs.cs125.questioner.lib

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.io.File

@Suppress("unused")
class Validator(
    questionsFile: File,
    private val sourceDir: String,
    private val seed: Int,
    private val maxMutationCount: Int
) {
    private val questionCache: Cache<String, Question> = Caffeine.newBuilder().maximumSize(4).recordStats().build()

    private val questions = loadFromPath(questionsFile, sourceDir).also {
        assert(it.isNotEmpty())
    }

    private fun QuestionCoordinates.getQuestion(json: String): Question = questionCache.get(path) {
        try {
            moshi.adapter(Question::class.java).fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun validate(
        name: String,
        verbose: Boolean = false,
        force: Boolean = false,
        testing: Boolean = false
    ): Pair<Question, ValidationReport?> {
        val questionString = questions[name] ?: error("no question named $name ")

        val questionCoordinates = moshi.adapter(QuestionCoordinates::class.java).fromJson(questionString)
        val question = questionCoordinates!!.getQuestion(questionString)

        if (question.validated && !force) {
            if (verbose) {
                println("$name: up-to-date")
            }
            return Pair(question, null)
        }
        if (!testing) {
            questionCoordinates.validationFile(sourceDir).delete()
            question.reportFile(sourceDir).delete()
        }
        try {
            question.validate(seed, maxMutationCount).also { report ->
                if (!testing) {
                    println("$name: ${report.summary}")
                    questionCoordinates.validationFile(sourceDir)
                        .writeText(moshi.adapter(Question::class.java).indent("  ").toJson(question))
                    question.reportFile(sourceDir).writeText(report.report())
                }
                return Pair(question, report)
            }
        } catch (e: ValidationFailed) {
            if (!testing) {
                question.reportFile(sourceDir).writeText(e.report(question))
            }
            throw e
        }
    }
}
