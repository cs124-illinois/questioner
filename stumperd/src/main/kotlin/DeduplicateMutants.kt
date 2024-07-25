package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.BsonDocument
import java.time.Instant

suspend fun Stumper.deduplicateMutants(deduplicateMutantsCollection: MongoCollection<BsonDocument>) = doStep(Stumper.Steps.DEDUPLICATE_MUTANTS) {
    val timestamp = Instant.now()
    val questionPath = "${question.published.author}/${question.published.path}"

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

    mutants = deduplicatedMutants
}
