package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import edu.illinois.cs.cs124.stumperd.Stumper.Steps
import edu.illinois.cs.cs125.jeed.core.Mutation
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import org.bson.BsonDocument
import org.bson.BsonString
import java.time.Instant

fun Int.toStep() = Steps.entries.find { it.value == this }

class Stumper(
    val index: Int,
    val timestamp: Instant,
    val id: String,
    val language: Language,
    val path: String,
    val contents: String,
    val author: String? = null
) {
    enum class Steps(val value: Int) {
        NONE(0),
        IDENTIFY(1),
        DEDUPLICATE(2),
        CLEAN(3),
        REDEDUPLICATE(4),
        VALIDATE(5),
        MUTATE(6),
        DEDUPLICATE_MUTANTS(7),
        VALIDATE_MUTANTS(8),
        DONE(16);

        companion object {
            fun forLimit(limit: Steps) = entries.filter { it.value <= limit.value }.sortedBy { it.value }
        }
    }

    var failedStep: Steps? = null
        private set

    var exception: Exception? = null
        set(value) {
            check(field == null && value != null)
            failedStep = currentStep
            field = value
        }

    val failed: Boolean
        get() = failedStep != null

    var completedStep: Steps = Steps.NONE
        private set(value) {
            check(!failed)
            field = value
        }

    val currentStep: Steps
        get() = Steps.entries.find { it.value == completedStep.value + 1 } ?: Steps.DONE

    // identify
    lateinit var question: Question

    // clean
    lateinit var cleanedContents: String

    data class MutatedSolution(val type: Mutation.Type, val contents: String)

    // mutants
    lateinit var mutants: Set<MutatedSolution>

    var limit: Steps? = null
        set(value) {
            check(field == null && value != null && value != Steps.DONE)
            field = value
        }

    suspend fun doStep(step: Steps, block: suspend Stumper.() -> Unit): Stumper {
        if (!failed && (limit == null || currentStep.value <= limit!!.value)) {
            try {
                block()
                completedStep = step
            } catch (e: Exception) {
                exception = e
            }
        }
        return this
    }

    fun recordDone(doneCollection: MongoCollection<BsonDocument>) {
        val timestamp = Instant.now()
        val update = if (exception == null) {
            Updates.combine(Updates.set("timestamp", timestamp), Updates.set("failed", false), Updates.unset("failure"))
        } else {
            val failureType = failedStep!!.name
            val message = exception!!.message ?: exception!!.cause?.message ?: ""
            Updates.combine(
                Updates.set("timestamp", timestamp),
                Updates.set("failed", true),
                Updates.set(
                    "failure",
                    BsonDocument()
                        .append("type", BsonString(failureType))
                        .append("message", BsonString(message)),
                ),
            )
        }
        doneCollection.updateOne(Filters.eq("id", id), update, UpdateOptions().upsert(true))
    }
}