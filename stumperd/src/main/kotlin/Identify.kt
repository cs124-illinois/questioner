package edu.illinois.cs.cs124.stumperd.server

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Projections
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonString

data class Identified(val submission: Submission, val contentHash: String, val path: String, val author: String)

class IdentificationFailure(submission: Submission, cause: Throwable) :
    StumperFailure(Steps.IDENTIFY, submission, cause)

fun Submission.identify(questionCollection: MongoCollection<BsonDocument>) = try {
    val query = BsonDocument().apply {
        append("latest", BsonBoolean(true))
        append("published.path", BsonString(path))
        if (author != null) {
            append("published.author", BsonString(author))
        }
    }
    val projection = Projections.include("published")

    val matchingQuestion = questionCollection
        .find(query)
        .projection(projection)
        .toList().also { matchingQuestions ->
            check(matchingQuestions.size == 1) { "Could not find question for $path" }
        }.first()

    matchingQuestion.getDocument("published").let { published ->
        val contentHash = published.getString("contentHash").value
        val path = published.getString("path").value
        val author = published.getString("author").value
        Identified(this, contentHash, path, author)
    }
} catch (e: Exception) {
    throw IdentificationFailure(this, e)
}
