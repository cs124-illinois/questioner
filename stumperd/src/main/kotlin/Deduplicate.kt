package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.BsonDocument
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant

suspend fun Stumper.deduplicate(deduplicateCollection: MongoCollection<BsonDocument>) =
    doStep(Stumper.Steps.DEDUPLICATE) {
        val timestamp = Instant.now()
        val contentHash = contents.md5()
        val questionPath = "${question.published.author}/${question.published.path}"
        val updateResult = deduplicateCollection.updateOne(
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

fun MongoCollection<BsonDocument>.addDeduplicateIndices(): MongoCollection<BsonDocument> {
    createIndex(Indexes.ascending("questionPath", "contentHash"), IndexOptions().unique(true))
    return this
}

internal fun String.md5() =
    BigInteger(1, MessageDigest.getInstance("MD5")!!.digest(toByteArray(Charsets.UTF_8))).toString(16).padStart(32, '0')
