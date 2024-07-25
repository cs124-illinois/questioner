package edu.illinois.cs.cs124.stumperd

import com.mongodb.client.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import org.bson.BsonDocument

data class PipelineOptions(
    val submissionCollection: MongoCollection<BsonDocument>,
    val questionCollection: MongoCollection<BsonDocument>,
    val stumperdCollections: StumperdCollections,
    val concurrency: Int = 1,
    val limit: Int = Int.MAX_VALUE,
    val stepLimit: Stumper.Steps? = null,
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
    input: ReceiveChannel<Stumper>,
    output: SendChannel<Stumper>,
) = launch {
    for (stumper in input) {
        options.stepLimit?.also { stumper.limit = it }

        stumper
            .identify(options.questionCollection)
            .deduplicate(options.stumperdCollections.deduplicateCollection)
            .clean()
            .rededuplicate(options.stumperdCollections.rededuplicateCollection)
            .validate()
            .mutate()
            .deduplicateMutants(options.stumperdCollections.deduplicateMutantsCollection)
            .validateMutants()

        output.send(stumper)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.collectSubmissions(options: PipelineOptions, input: ReceiveChannel<Stumper>) = produce {
    var waitingFor = 0
    val received = mutableListOf<Stumper>()
    for (result in input) {
        result.recordDone(options.stumperdCollections.doneCollection)

        received += result
        received.sortBy { it.index }

        while (received.firstOrNull()?.index == waitingFor) {
            waitingFor++
            val latest = received.removeFirst()
            send(latest)
        }
    }
}

fun CoroutineScope.pipeline(options: PipelineOptions): ReceiveChannel<Stumper> {
    val producer = produceSubmissions(options)
    val output = Channel<Stumper>()
    val jobs = (0 until options.concurrency).map { id -> launchStumperProcessor(id, options, producer, output) }
    launch {
        jobs.forEach { job -> job.join() }
        output.close()
    }
    return collectSubmissions(options, output)
}
