package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import org.bson.BsonDocument
import org.bson.BsonString
import java.time.Instant

data class SubmissionResult(val submission: Submission, val exception: Exception? = null) {
    fun recordDone(doneCollection: MongoCollection<BsonDocument>) {
        val timestamp = Instant.now()
        val update = if (exception == null) {
            Updates.combine(Updates.set("timestamp", timestamp), Updates.set("failed", false), Updates.unset("failure"))
        } else {
            val failureType = when (exception) {
                is StumperFailure -> exception.step.name
                else -> "unknown"
            }
            val message = exception.message ?: exception.cause?.message ?: ""
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
        doneCollection.updateOne(Filters.eq("id", submission.id), update, UpdateOptions().upsert(true))
    }

    fun recordTimestamp(statusCollection: MongoCollection<BsonDocument>) {
        val timestamp = Instant.now()
        statusCollection.updateOne(
            Filters.eq("_id", "latestTimestamp"),
            Updates.combine(
                Updates.set("timestamp", submission.timestamp),
                Updates.set("updated", timestamp),
            ),
            UpdateOptions().upsert(true),
        )
    }
}

data class PipelineOptions(
    val submissionCollection: MongoCollection<BsonDocument>,
    val questionCollection: MongoCollection<BsonDocument>,
    val stumperdCollections: StumperdCollections,
    val doneCallback: (done: SubmissionResult) -> Unit = {},
    val concurrency: Int = 1,
    val limit: Int = Int.MAX_VALUE,
    val stepLimit: Int = Int.MAX_VALUE,
)

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.produceSubmissions(options: PipelineOptions) =
    produce {
        for (starter in options.submissionCollection.findSubmissions().take(options.limit)) {
            send(starter)
        }
    }

private class FinishStep : Exception()

fun CoroutineScope.launchStumperProcessor(
    @Suppress("UNUSED_PARAMETER") id: Int,
    options: PipelineOptions,
    input: ReceiveChannel<Submission>,
    output: SendChannel<SubmissionResult>,
) = launch {
    fun checkLimit(step: Steps) {
        if (options.stepLimit < step.value) {
            throw FinishStep()
        }
    }

    for (submission in input) {
        try {
            checkLimit(Steps.IDENTIFY)
            val identified = submission.identify(options.questionCollection)

            checkLimit(Steps.DEDUPLICATE)
            val deduplicated = identified.deduplicate(options.stumperdCollections.deduplicateCollection)

            checkLimit(Steps.CLEAN)
            val cleaned = deduplicated.clean()

            checkLimit(Steps.REDEDUPLICATE)
            val rededuplicated =
                cleaned.rededuplicate(options.stumperdCollections.rededuplicateCollection)

            checkLimit(Steps.VALIDATE)
            val validated = rededuplicated.validate()

            checkLimit(Steps.MUTATE)
            val mutated = validated.mutate()

            checkLimit(Steps.DEDUPLICATE_MUTANTS)
            val deduplicateMutants =
                mutated.deduplicateMutants(options.stumperdCollections.deduplicateMutantsCollection)

            checkLimit(Steps.VALIDATE_MUTANTS)
            val validatedMutants = deduplicateMutants.validateMutants()

            output.send(SubmissionResult(submission))
        } catch (_: FinishStep) {
            output.send(SubmissionResult(submission))
        } catch (e: Exception) {
            output.send(SubmissionResult(submission, e))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.collectSubmissions(options: PipelineOptions, input: ReceiveChannel<SubmissionResult>) = produce {
    var waitingFor = 0
    val received = mutableListOf<SubmissionResult>()
    for (result in input) {
        result.recordDone(options.stumperdCollections.doneCollection)

        received += result
        received.sortBy { it.submission.index }

        while (received.firstOrNull()?.submission?.index == waitingFor) {
            waitingFor++
            val latest = received.removeFirst()
            latest.recordTimestamp(options.stumperdCollections.statusCollection)
            send(latest)
        }
    }
}

fun CoroutineScope.pipeline(options: PipelineOptions): ReceiveChannel<SubmissionResult> {
    val producer = produceSubmissions(options)
    val output = Channel<SubmissionResult>()
    val jobs = (0 until options.concurrency).map { id -> launchStumperProcessor(id, options, producer, output) }
    launch {
        jobs.forEach { job -> job.join() }
        output.close()
    }
    return collectSubmissions(options, output)
}
