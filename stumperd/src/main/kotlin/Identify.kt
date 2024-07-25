package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Projections
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonString

data class Identified(val submission: Submission, val question: Question)

class IdentifyFailure(cause: Throwable) : StumperFailure(Steps.IDENTIFY, cause)

fun Submission.identify(questionCollection: MongoCollection<BsonDocument>) = try {
    val query = BsonDocument().apply {
        append("latest", BsonBoolean(true))
        append("published.path", BsonString(path))
        if (author != null) {
            append("published.author", BsonString(author))
        }
    }
    val projection = Projections.exclude("_id")

    val matchingQuestion = questionCollection
        .find(query)
        .projection(projection)
        .toList().also { matchingQuestions ->
            check(matchingQuestions.size == 1) { "Could not find question for $path" }
        }.first()

    Identified(this, moshi.adapter(Question::class.java).fromJson(matchingQuestion.toJson())!!)
} catch (e: Exception) {
    throw IdentifyFailure(e)
}
