@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.stumpers

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.googleFormat
import edu.illinois.cs.cs125.jeed.core.hasBadWords
import edu.illinois.cs.cs125.jeed.core.ktFormat
import edu.illinois.cs.cs125.jeed.core.stripAssertionMessages
import edu.illinois.cs.cs125.jeed.core.stripComments
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.deTemplate
import edu.illinois.cs.cs125.questioner.lib.templateSubmission
import org.bson.BsonDocument
import java.time.Instant

data class Candidate(
    val submitted: Instant,
    val contents: String,
    val originalID: String,
    val question: Question,
    val language: Language
) {
    val contentsHash = contents.md5()

    fun exists(collection: MongoCollection<BsonDocument>) =
        collection.countDocuments(Filters.eq("originalID", originalID)) > 0 ||
            collection.countDocuments(
                Filters.and(
                    Filters.eq("coordinates.language", language.toString()),
                    Filters.eq("coordinates.path", question.published.path),
                    Filters.eq("coordinates.author", question.published.author),
                    Filters.eq("hashes.original", contentsHash)
                )
            ) > 0
}

suspend fun Candidate.clean(): Solution {
    val template = question.getTemplate(language)

    val fileType = when (language) {
        Language.kotlin -> Source.FileType.KOTLIN
        Language.java -> Source.FileType.JAVA
    }

    val cleanedSource = contents.stripComments(fileType).let {
        if (template != null) {
            "// TEMPLATE_START\n$it\n// TEMPLATE_END \n"
        } else {
            it
        }
    }.let { contents ->
        question.templateSubmission(contents, language)
    }.stripAssertionMessages()

    val hasBadWords = cleanedSource.hasBadWords(question.badWords()) != null

    val formattedSource = when (cleanedSource.type) {
        Source.SourceType.JAVA -> cleanedSource.googleFormat()
        Source.SourceType.KOTLIN -> cleanedSource.ktFormat(KtLintArguments(failOnError = false, indent = 2))
        else -> error("Invalid source type: ${cleanedSource.type}")
    }.contents.deTemplate(template)

    return Solution(
        submitted,
        formattedSource,
        Solution.Hashes(contentsHash, formattedSource.md5()),
        hasBadWords,
        originalID,
        Coordinates(language, question.published.path, question.published.author),
        valid = false
    )
}