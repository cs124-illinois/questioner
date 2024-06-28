package edu.illinois.cs.cs125.questioner.lib.server

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question

@JsonClass(generateAdapter = true)
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