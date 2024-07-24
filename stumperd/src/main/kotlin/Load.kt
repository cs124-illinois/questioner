package edu.illinois.cs.cs124.stumperd.server

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import edu.illinois.cs.cs125.questioner.lib.Language
import org.bson.BsonDocument
import java.time.Instant

private val beginningOfStumperdTime = Instant.parse("2020-01-01T00:00:00Z")

data class Submission(
    val index: Int,
    val timestamp: Instant,
    val id: String,
    val language: Language,
    val path: String,
    val contents: String,
    val author: String? = null
) {
    enum class Type {
        QUALITY, PASSED, SUCCEEDED_NO_FAILURES
    }
}

fun Sequence<BsonDocument>.filterResults() = filter { bsonDocument ->
    bsonDocument.getString("type", null)?.value == "results"
}

// Circa Fall 2020
private fun BsonDocument.getPassed() = getBoolean("passed", null)?.value

// Circa Fall 2021 and Fall 2022
private fun BsonDocument.getSucceededNoFailures() = getDocument("testingResults", null)
    ?.let { testingResults ->
        val succeeded = testingResults.getBoolean("succeeded", null)?.value
        val failureCount = testingResults.getInt32("failureCount", null)?.value
        when {
            succeeded == null || failureCount == null -> null
            else -> succeeded == true && failureCount == 0
        }
    }

// Since Fall 2023
private fun BsonDocument.getQuality() = getDocument("testingResults", null)
    ?.getDocument("complete", null)
    ?.getDocument("partial", null)
    ?.getDocument("passedSteps", null)
    ?.getBoolean("quality", null)?.value


fun Sequence<BsonDocument>.filterType(type: Submission.Type) = filter { bsonDocument ->
    when (type) {
        Submission.Type.PASSED -> bsonDocument.getPassed() == true
        Submission.Type.SUCCEEDED_NO_FAILURES -> bsonDocument.getQuality() == null && bsonDocument.getSucceededNoFailures() == true
        Submission.Type.QUALITY -> bsonDocument.getQuality() == true
    }
}

fun MongoCollection<BsonDocument>.asSequence(
    startTime: Instant = beginningOfStumperdTime
): Sequence<BsonDocument> {
    createIndex(Indexes.ascending("timestamp"))

    val query = Filters.gte("timestamp", startTime)
    val sort = Sorts.ascending("timestamp")
    val projection = Projections.include(
        "type",
        "timestamp",
        "semester",
        // 2023
        "testingResults.complete.partial.passedSteps.quality",
        // 2022
        "testingResults.succeeded",
        "testingResults.failureCount",
        // 2021
        "points.total",
        // 2020
        "passed",
        // For submission
        "id",
        "language",
        "submission"
    )
    val starters = find(query).projection(projection).sort(sort).batchSize(32).noCursorTimeout(true)

    return sequence { starters.forEach { document -> yield(document) } }.filterNotNull()
}

fun MongoCollection<BsonDocument>.findSubmissions(
    startTime: Instant = beginningOfStumperdTime
): Sequence<Submission> = asSequence(startTime)
    .filterResults()
    .filter { bsonDocument ->
        bsonDocument.getQuality() ?: bsonDocument.getPassed() ?: bsonDocument.getSucceededNoFailures() ?: false
    }.mapIndexed { index, bsonDocument ->
        val timestamp = Instant.ofEpochMilli(bsonDocument.getDateTime("timestamp").value)
        val id = bsonDocument.getString("id").value!!
        val submission = bsonDocument.getDocument("submission")!!
        val language =
            Language.valueOf(submission.getString("language", null)?.value ?: bsonDocument.getString("language").value)
        val path = submission.getString("path").value!!
        val contents = submission.getString("contents").value!!
        val author = submission.getString("author", null)?.value
        Submission(index, timestamp, id, language, path, contents, author)
    }
