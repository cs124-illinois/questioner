package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.BsonDocument
import java.time.Instant

suspend fun Stumper.rededuplicate(rededuplicateCollection: MongoCollection<BsonDocument>) = doStep(Stumper.Steps.REDEDUPLICATE) {
    val timestamp = Instant.now()
    val contentHash = cleanedContents.md5()
    val questionPath = "${question.published.author}/${question.published.path}"
    val updateResult = rededuplicateCollection.updateOne(
        Filters.and(Filters.eq("questionPath", questionPath), Filters.eq("contentHash", contentHash)),
        Updates.combine(
            Updates.set("questionPath", questionPath),
            Updates.set("contentHash", contentHash),
            Updates.set("timestamp", timestamp),
        ),
        UpdateOptions().upsert(true),
    )
    check(updateResult.upsertedId != null) { "Duplicate submission for $questionPath" }
}
