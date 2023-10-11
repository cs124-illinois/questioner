@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.stumpers

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.UpdateOptions
import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.questioner.lib.Language
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.Document
import org.bson.json.JsonWriterSettings
import java.time.Instant
import edu.illinois.cs.cs125.questioner.lib.moshi
import kotlin.random.Random

private val jsonWriterSettings = JsonWriterSettings
    .builder()
    .dateTimeConverter { value, writer ->
        writer.writeString(Instant.ofEpochMilli(value).toString())
    }.build()

@JsonClass(generateAdapter = true)
data class Coordinates(
    val language: Language,
    val path: String,
    val author: String,
)

@Suppress("MemberVisibilityCanBePrivate", "unused")
@JsonClass(generateAdapter = true)
data class Solution(
    val submitted: Instant,
    val contents: String,
    val hashes: Hashes,
    val hasBadWords: Boolean,
    val originalID: String,
    val coordinates: Coordinates,
    val valid: Boolean,
    val validation: Validation? = null,
    val processed: Boolean = false,
    val randomValue: Int = Random.nextInt()
) {
    @JsonClass(generateAdapter = true)
    data class Hashes(val original: String, val cleaned: String)

    @Transient
    var from: MongoCollection<BsonDocument>? = null

    @JsonClass(generateAdapter = true)
    data class Validation(
        val validated: Instant,
        val questionVersion: String,
        val questionHash: String,
        val questionerVersion: String,
    )

    val json: String = moshi.adapter(Solution::class.java).indent("  ").toJson(this)
    val document: BsonDocument = BsonDocument.parse(json).also { document ->
        document["submitted"] = BsonDateTime(submitted.toEpochMilli())
        validation?.validated?.also { validated ->
            document.getDocument("validation")["validated"] = BsonDateTime(validated.toEpochMilli())
        }
    }

    fun exists(collection: MongoCollection<BsonDocument>) =
        collection.countDocuments(Filters.eq("originalID", originalID)) > 0 ||
            collection.countDocuments(
                Filters.and(
                    Filters.eq("coordinates.language", coordinates.language.toString()),
                    Filters.eq("coordinates.path", coordinates.path),
                    Filters.eq("coordinates.author", coordinates.author),
                    Filters.eq("hashes.cleaned", hashes.cleaned)
                )
            ) > 0

    fun save(collection: MongoCollection<BsonDocument>? = from) {
        check(collection != null) { "Can't save into an empty collection" }
        collection.updateOne(
            Filters.and(Filters.eq("originalID", originalID)),
            Document("${"$"}set", document),
            UpdateOptions().upsert(true),
        )
    }
}

fun MongoCollection<BsonDocument>.createInsertionIndices() = apply {
    createIndex(Document().append("originalID", 1), IndexOptions().unique(true))
    createIndex(
        Document().append("coordinates.language", 1).append("coordinates.path", 1).append("coordinates.author", 1)
            .append("hashes.original", 1), IndexOptions().unique(true)
    )
    createIndex(
        Document().append("coordinates.language", 1).append("coordinates.path", 1).append("coordinates.author", 1)
            .append("hashes.cleaned", 1), IndexOptions().unique(true)
    )
}


fun MongoCollection<BsonDocument>.getUnvalidated(limit: Int = Int.MAX_VALUE): Sequence<Solution> {
    return find(
        Filters.and(
            Filters.ne("validated", true),
            Filters.eq("hasBadWords", false)
        )
    ).limit(limit).asSequence().map { document ->
        moshi.adapter(Solution::class.java).fromJson(document.toJson(jsonWriterSettings))?.also { solution ->
            solution.from = this@getUnvalidated
        }
    }.filterNotNull()
}