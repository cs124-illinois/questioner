package edu.illinois.cs.cs125.questioner.server

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import kotlinx.serialization.Serializable
import org.bson.BsonDocument

@Serializable
data class CacheStats(val hits: Long, val misses: Long) {
    constructor(caffeineStats: CacheStats) : this(caffeineStats.hitCount(), caffeineStats.missCount())
}

internal val questionCache: Cache<String, Question?> =
    Caffeine.newBuilder().maximumSize(questionCacheSize).recordStats().build()

internal fun getStats() = CacheStats(questionCache.stats())

internal fun getQuestionByPath(
    path: String,
    collection: MongoCollection<BsonDocument> = questionerCollection,
): Question? = collection.find(
    Filters.and(Filters.eq("published.path", path), Filters.eq("latest", true)),
).sort(Sorts.descending("updated")).let { results ->
    if (results.count() == 0) {
        return null
    }
    check(results.count() == 1) { "Found multiple path matches" }
    return json.decodeFromString<Question>(results.first()!!.toJson()).also { question ->
        question.warm()
    }
}

internal fun Submission.getQuestion(testingQuestions: Map<String, Question>? = null) = questionCache.get(contentHash) {
    if (testingQuestions != null) {
        return@get testingQuestions.values.find { it.published.contentHash == contentHash }
    }
    questionerCollection.find(
        Filters.and(Filters.eq("published.contentHash", contentHash)),
    ).sort(Sorts.descending("updated")).let { results ->
        if (results.count() == 0) {
            return@get null
        }
        check(results.count() == 1) { "Found multiple contentHash matches" }
        try {
            json.decodeFromString<Question>(results.first()!!.toJson())
        } catch (e: Exception) {
            logger.warn { "Couldn't load question $contentHash, which might use an old schema: $e" }
            null
        }
    }
}
