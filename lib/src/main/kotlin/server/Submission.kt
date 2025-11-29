package edu.illinois.cs.cs125.questioner.lib.server

import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import kotlinx.serialization.Serializable

@Serializable
data class Submission(
    val type: SubmissionType,
    val contentHash: String,
    val language: Language,
    val contents: String,
    val originalID: String? = null,
    val testTestingSettings: Question.TestTestingSettings? = null,
) {
    enum class SubmissionType { SOLVE, TESTTESTING }
}

fun Question.toSubmission(type: Submission.SubmissionType, language: Language, contents: String) =
    Submission(type, published.contentHash, language, contents)