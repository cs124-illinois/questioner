import edu.illinois.cs.cs124.stumperd.server.DeduplicateFailure
import edu.illinois.cs.cs124.stumperd.server.IdentifyFailure
import edu.illinois.cs.cs124.stumperd.server.Steps
import edu.illinois.cs.cs124.stumperd.server.pipeline
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.toList

class TestDeduplicate : StringSpec() {
    override suspend fun beforeSpec(spec: Spec) {
        testCollections.reset()
    }
    init {
        "it should deduplicate submissions correctly" {
            val results = pipeline(pipelineOptions.copy(stepLimit = Steps.DEDUPLICATE.value)).toList()

            results.count { it.exception is IdentifyFailure } shouldBe 1
            results.count { it.exception is DeduplicateFailure } shouldBe 395

            testCollections.doneCollection.successCount shouldBeGreaterThan 0
            testCollections.doneCollection.successCount shouldBe results.count() - 1 - 395
            testCollections.doneCollection.failureCount shouldBe 1 + 395
            testCollections.doneCollection.failureCountForStep(Steps.IDENTIFY) shouldBe 1
            testCollections.doneCollection.failureCountForStep(Steps.DEDUPLICATE) shouldBe 395
        }
    }
}
