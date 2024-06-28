package edu.illinois.cs.cs125.questioner.lib.stumpers

import com.mongodb.client.MongoCollection
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.logger
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import org.bson.BsonDocument
import java.time.Instant

suspend fun MongoCollection<BsonDocument>.addStumperSolution(
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
    if (candidate.exists(this)) {
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
    if (solution.exists(this)) {
        return
    }
    try {
        solution.save(this)
    } catch (e: Exception) {
        logger.warn { e }
    }
}
