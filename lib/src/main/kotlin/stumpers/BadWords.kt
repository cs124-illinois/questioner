package edu.illinois.cs.cs125.questioner.lib.stumpers

import edu.illinois.cs.cs125.jeed.core.getBadWords
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.templateSubmission

private val badWords = mutableMapOf<Question, Set<String>>()

fun Question.badWords() = badWords.getOrPut(this) {
    correct.contents.let {
        if (getTemplate(Language.java) != null) {
            "// TEMPLATE_START\n$it\n// TEMPLATE_END \n"
        } else {
            it
        }
    }.let { contents ->
        templateSubmission(contents, Language.java)
    }.getBadWords()
}
