@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.stumpers

import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.test
import java.time.Instant

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
            question.published.contentHash,
            VERSION
        )
    )
}