import edu.illinois.cs.cs124.stumperd.server.PipelineOptions
import edu.illinois.cs.cs124.stumperd.server.StumperdCollections
import edu.illinois.cs.cs124.stumperd.server.collection
import edu.illinois.cs.cs124.stumperd.server.empty

val submissionsCollection by lazy {
    System.getenv("MONGODB_TESTING").collection("results")
}
val questionsCollection by lazy {
    System.getenv("MONGODB_TESTING").collection("questions")
}

fun StumperdCollections.reset(): StumperdCollections {
    deduplicateCollection.empty()
    rededuplicateCollection.empty()
    doneCollection.empty()
    statusCollection.empty()
    return this
}

val testCollections = StumperdCollections(System.getenv("MONGODB_TESTING")).reset()

val pipelineOptions = PipelineOptions(
    submissionsCollection,
    questionsCollection,
    testCollections,
    concurrency = 8,
)
