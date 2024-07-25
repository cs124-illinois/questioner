package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Projections
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import io.ktor.util.collections.ConcurrentMap
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonString

suspend fun Stumper.identify(questionCollection: MongoCollection<BsonDocument>) = doStep(Stumper.Steps.IDENTIFY) {
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

    val contentHash = matchingQuestion.getDocument("published").getString("contentHash").value
    question = jsonCache.getOrPut(contentHash) {
        moshi.adapter(Question::class.java).fromJson(matchingQuestion.toJson())!!
    }
}

private val jsonCache = ConcurrentMap<String, Question>()