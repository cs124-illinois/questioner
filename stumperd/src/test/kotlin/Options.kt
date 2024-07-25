import edu.illinois.cs.cs124.stumperd.PipelineOptions
import edu.illinois.cs.cs124.stumperd.Stumper
import edu.illinois.cs.cs124.stumperd.StumperdCollections
import edu.illinois.cs.cs124.stumperd.collection
import edu.illinois.cs.cs124.stumperd.empty

val submissionsCollection by lazy {
    System.getenv("MONGODB_TESTING").collection("results")
}
val questionsCollection by lazy {
    System.getenv("MONGODB_TESTING").collection("questions")
}

fun StumperdCollections.reset(): StumperdCollections {
    deduplicateCollection.empty()
    rededuplicateCollection.empty()
    deduplicateMutantsCollection.empty()

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

data class FailureCounts(
    val correctCount: Int,
    val identifyFailureCount: Int = 0,
    val deduplicateFailureCount: Int = 0,
    val cleanFailureCount: Int = 0,
    val rededuplicateFailureCount: Int = 0,
    val validationFailureCount: Int = 0,
    val mutationFailureCount: Int = 0,
    val deduplicateMutantsFailureCount: Int = 0,
    val validateMutantsFailureCount: Int = 0,
) {
    fun failureCount(stepLimit: Stumper.Steps) = Stumper.Steps.forLimit(stepLimit).sumOf { step -> countForStep(step) }
    fun successCount(stepLimit: Stumper.Steps) = correctCount - failureCount(stepLimit)

    fun countForStep(step: Stumper.Steps) = when (step) {
        Stumper.Steps.IDENTIFY -> identifyFailureCount
        Stumper.Steps.DEDUPLICATE -> deduplicateFailureCount
        Stumper.Steps.CLEAN -> cleanFailureCount
        Stumper.Steps.REDEDUPLICATE -> rededuplicateFailureCount
        Stumper.Steps.VALIDATE -> validationFailureCount
        Stumper.Steps.MUTATE -> mutationFailureCount
        Stumper.Steps.DEDUPLICATE_MUTANTS -> deduplicateMutantsFailureCount
        Stumper.Steps.VALIDATE_MUTANTS -> validateMutantsFailureCount
        //
        Stumper.Steps.DONE -> 0
        Stumper.Steps.NONE -> 0
    }
}
