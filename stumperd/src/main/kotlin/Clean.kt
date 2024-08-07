package edu.illinois.cs.cs124.stumperd

import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.getBadWords
import edu.illinois.cs.cs125.jeed.core.googleFormat
import edu.illinois.cs.cs125.jeed.core.hasBadWords
import edu.illinois.cs.cs125.jeed.core.ktFormat
import edu.illinois.cs.cs125.jeed.core.stripAssertionMessages
import edu.illinois.cs.cs125.jeed.core.stripComments
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.deTemplate
import edu.illinois.cs.cs125.questioner.lib.templateSubmission

suspend fun Stumper.clean() = doStep(Stumper.Steps.CLEAN) {
    val template = question.getTemplate(language)

    val fileType = when (language) {
        Language.kotlin -> Source.FileType.KOTLIN
        Language.java -> Source.FileType.JAVA
    }

    val templatedCleanedContents = contents.stripComments(fileType).let {
        if (template != null) {
            "// TEMPLATE_START\n$it\n// TEMPLATE_END \n"
        } else {
            it
        }
    }.let { contents ->
        question.templateSubmission(contents, language)
    }.stripAssertionMessages()

    templatedCleanedContents.hasBadWords(question.badWords())?.also { error("Has bad words: $it") }

    cleanedContents = when (templatedCleanedContents.type) {
        Source.SourceType.JAVA -> templatedCleanedContents.googleFormat()
        Source.SourceType.KOTLIN -> templatedCleanedContents.ktFormat(KtLintArguments(failOnError = false, indent = 2))
        else -> error("Invalid source type: ${templatedCleanedContents.type}")
    }.contents.deTemplate(template)
}

private val badWords = mutableMapOf<Question, Set<String>>()

private fun Question.badWords() = badWords.getOrPut(this) {
    getCorrect(Language.java)!!.let {
        if (getTemplate(Language.java) != null) {
            "// TEMPLATE_START\n$it\n// TEMPLATE_END \n"
        } else {
            it
        }
    }.let { contents ->
        templateSubmission(contents, Language.java)
    }.getBadWords()
}
