package edu.illinois.cs.cs124.stumperd.server

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bson.BsonDocument
import org.bson.BsonString
import java.time.Instant
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.produceNumbers() = produce {
    for (i in 0 until 16) {
        send(i)
        delay(Random.nextLong(100))
    }
}

fun CoroutineScope.launchNumberProcessor(input: ReceiveChannel<Int>, output: SendChannel<Int>) = launch {
    for (number in input) {
        delay(Random.nextLong(1000))
        output.send(number)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.collectNumbers(channel: ReceiveChannel<Int>) = produce {
    var waitingFor = 0
    val received = mutableListOf<Int>()
    for (number in channel) {
        received += number
        received.sort()
        while (received.firstOrNull() == waitingFor) {
            waitingFor++
            send(received.removeFirst())
        }
    }
}

suspend fun numberPipeline() = coroutineScope {
    val producer = produceNumbers()
    val output = Channel<Int>()
    val jobs = (0 until 4).map { launchNumberProcessor(producer, output) }
    launch {
        jobs.forEach { it.join() }
        output.close()
    }
    for (number in collectNumbers(output)) {
        println(number)
    }
}

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
                        .append("message", BsonString(message))
                )
            )
        }
        doneCollection.updateOne(Filters.eq("id", submission.id), update, UpdateOptions().upsert(true))
    }

    fun recordTimestamp(statusCollection: MongoCollection<BsonDocument>) {
        val timestamp = Instant.now()
        statusCollection.updateOne(
            Filters.eq("latestTimestamp"),
            Updates.combine(
                Updates.set("timestamp", submission.timestamp),
                Updates.set("updated", timestamp)
            ),
            UpdateOptions().upsert(true)
        )
    }
}

data class PipelineOptions(
    val submissionCollection: MongoCollection<BsonDocument>,
    val questionCollection: MongoCollection<BsonDocument>,
    val doneCollection: MongoCollection<BsonDocument>? = null,
    val statusCollection: MongoCollection<BsonDocument>? = null,
    val doneCallback: (done: SubmissionResult) -> Unit = {},
    val concurrency: Int = 1,
    val limit: Int = Int.MAX_VALUE
)

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.produceSubmissions(options: PipelineOptions) =
    produce {
        for (starter in options.submissionCollection.findSubmissions().take(options.limit)) {
            send(starter)
        }
    }

fun CoroutineScope.launchStumperProcessor(
    @Suppress("UNUSED_PARAMETER") id: Int,
    options: PipelineOptions,
    input: ReceiveChannel<Submission>,
    output: SendChannel<SubmissionResult>
) = launch {
    for (submission in input) {
        try {
            submission.identify(options.questionCollection)
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
        options.doneCollection?.also { result.recordDone(it) }

        received += result
        received.sortBy { it.submission.index }

        while (received.firstOrNull()?.submission?.index == waitingFor) {
            waitingFor++
            val latest = received.removeFirst()
            options.statusCollection?.also { result.recordTimestamp(it) }
            send(latest)
        }
    }
}

fun CoroutineScope.pipeline(options: PipelineOptions): ReceiveChannel<SubmissionResult>  {
    val producer = produceSubmissions(options)
    val output = Channel<SubmissionResult>()
    val jobs = (0 until options.concurrency).map { id -> launchStumperProcessor(id, options, producer, output) }
    launch {
        jobs.forEach { job -> job.join() }
        output.close()
    }
    return collectSubmissions(options, output)
}