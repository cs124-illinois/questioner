@file:Suppress("ktlint:standard:filename")

package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import org.bson.BsonDocument
import java.time.Instant

class StumperdCollections(mongodb: String) {
    val deduplicateCollection by lazy {
        mongodb.collection("stumperd_deduplicate").addDeduplicateIndices()
    }

    val rededuplicateCollection by lazy {
        mongodb.collection("stumperd_rededuplicate").addDeduplicateIndices()
    }

    val deduplicateMutantsCollection by lazy {
        mongodb.collection("stumperd_deduplicatemutants").addDeduplicateIndices()
    }

    class DoneCollection(collection: MongoCollection<BsonDocument>) : MongoCollection<BsonDocument> by collection {
        init {
            createIndex(Indexes.ascending("failed"))
        }

        val successCount: Long
            get() = countDocuments(Filters.eq("failed", false))
        val failureCount: Long
            get() = countDocuments(Filters.eq("failed", true))

        fun failureCountForStep(step: Stumper.Steps) = countDocuments(Filters.and(Filters.eq("failed", true), Filters.eq("failure.type", step.name)))
    }

    val doneCollection by lazy {
        DoneCollection(mongodb.collection("stumperd_done"))
    }

    class StatusCollection(collection: MongoCollection<BsonDocument>) : MongoCollection<BsonDocument> by collection {
        val latestTimestamp: Instant
            get() = find(Filters.eq("_id", "latestTimestamp")).first()!!.getDateTime("timestamp").let {
                Instant.ofEpochMilli(it.value)
            }
    }
    val statusCollection by lazy {
        StatusCollection(mongodb.collection("stumperd_status"))
    }
}
