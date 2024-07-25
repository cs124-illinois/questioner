package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.BsonDocument
import java.time.Instant

class DeduplicateMutantsFailure(cause: Throwable) : StumperFailure(Steps.REDEDUPLICATE, cause)

typealias DeduplicatedMutants = Mutated

fun Mutated.deduplicateMutants(deduplicateMutantsCollection: MongoCollection<BsonDocument>): DeduplicatedMutants = try {
    val timestamp = Instant.now()
    val questionPath = "${validated.identified.question.published.author}/${validated.identified.question.published.path}"

    val deduplicatedMutants = mutants.filter { mutant ->
        val contentHash = mutant.contents.md5()
        val updateResult = deduplicateMutantsCollection.updateOne(
            Filters.and(Filters.eq("questionPath", questionPath), Filters.eq("contentHash", contentHash)),
            Updates.combine(
                Updates.set("questionPath", questionPath),
                Updates.set("contentHash", contentHash),
                Updates.set("timestamp", timestamp),
            ),
            UpdateOptions().upsert(true),
        )
        updateResult.upsertedId != null
    }.toSet()

    check(deduplicatedMutants.isNotEmpty()) { "Solution generated no new mutants" }

    DeduplicatedMutants(validated, deduplicatedMutants)
} catch (e: Exception) {
    throw DeduplicateMutantsFailure(e)
}
