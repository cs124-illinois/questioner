package edu.illinois.cs.cs125.questioner.server

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import edu.illinois.cs.cs125.questioner.lib.server.Submission

@JsonClass(generateAdapter = true)
data class CacheStats(val hits: Long, val misses: Long) {
    constructor(caffeineStats: CacheStats) : this(caffeineStats.hitCount(), caffeineStats.missCount())
}

internal val questionCache: Cache<String, Question?> =
    Caffeine.newBuilder().maximumSize(questionCacheSize).recordStats().build()

internal fun getStats() = CacheStats(questionCache.stats())

internal fun getQuestionByPath(path: String): Question? = questionerCollection.find(
    Filters.and(Filters.eq("published.path", path), Filters.eq("latest", true)),
).sort(Sorts.descending("updated")).let { results ->
    if (results.count() == 0) {
        return null
    }
    check(results.count() == 1) { "Found multiple path matches" }
    return moshi.adapter(Question::class.java).fromJson(results.first()!!.toJson())?.also { question ->
        question.warm()
    }
}

internal fun Submission.getQuestion() = questionCache.get(contentHash) {
    questionerCollection.find(
        Filters.and(Filters.eq("published.contentHash", contentHash)),
    ).sort(Sorts.descending("updated")).let { results ->
        if (results.count() == 0) {
            return@get null
        }
        check(results.count() == 1) { "Found multiple contentHash matches" }
        try {
            moshi.adapter(Question::class.java).fromJson(results.first()!!.toJson())
        } catch (e: Exception) {
            logger.warn { "Couldn't load question $contentHash, which might use an old schema: $e" }
            null
        }
    }
}
