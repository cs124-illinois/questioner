@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.stumpers

import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.test
import java.time.Instant
import java.util.Properties

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs124.questioner.lib.version"))
}.getProperty("version")

fun TestResults.validated() = complete.partial?.passedSteps?.quality == true
suspend fun Solution.validate(question: Question): Solution {
    check(question.published.path == coordinates.path && question.published.author == coordinates.author) {
        "Question passed to validate does not match coordinates"
    }

    val testResults = question.test(contents, coordinates.language, settings = question.testingSettings!!)

    return this.copy(
        valid = testResults.validated(),
        validation = Solution.Validation(
            Instant.now(),
            question.published.version,
            question.metadata.contentHash,
            VERSION
        )
    )
}