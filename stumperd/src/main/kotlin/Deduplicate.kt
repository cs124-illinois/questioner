package edu.illinois.cs.cs124.stumperd.server

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

class DeduplicateFailure(cause: Throwable) : StumperFailure(Steps.DEDUPLICATE, cause)

fun MongoCollection<BsonDocument>.addDeduplicateIndices(): MongoCollection<BsonDocument> {
    createIndex(Indexes.ascending("questionPath", "contentHash"), IndexOptions().unique(true))
    return this
}

internal fun String.md5() =
    BigInteger(1, MessageDigest.getInstance("MD5")!!.digest(toByteArray(Charsets.UTF_8))).toString(16).padStart(32, '0')

typealias Deduplicated = Identified

fun Identified.deduplicate(deduplicateCollection: MongoCollection<BsonDocument>): Deduplicated = try {
    val timestamp = Instant.now()
    val contentHash = submission.contents.md5()
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
    this
} catch (e: Exception) {
    throw DeduplicateFailure(e)
}
