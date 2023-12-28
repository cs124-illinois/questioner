package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.stumpers.Candidate
import edu.illinois.cs.cs125.questioner.lib.stumpers.Solution
import edu.illinois.cs.cs125.questioner.lib.stumpers.clean
import edu.illinois.cs.cs125.questioner.lib.stumpers.validated
import java.time.Instant

internal suspend fun addStumperSolution(
    submitted: Instant,
    submission: Submission,
    testResults: TestResults,
    question: Question,
) {
    if (!testResults.validated() || submission.originalID == null) {
        return
    }
    val candidate = Candidate(
        submitted,
        submission.contents,
        submission.originalID,
        question,
        submission.language,
    )
    if (candidate.exists(stumperSolutionCollection)) {
        return
    }
    val solution = try {
        candidate.clean().copy(
            valid = true,
            validation = Solution.Validation(
                submitted,
                question.published.version,
                question.published.contentHash,
                VERSION,
            ),
        )
    } catch (e: Exception) {
        logger.warn { e }
        return
    }
    if (solution.exists(stumperSolutionCollection)) {
        return
    }
    try {
        solution.save(stumperSolutionCollection)
    } catch (e: Exception) {
        logger.warn { e }
    }
}
