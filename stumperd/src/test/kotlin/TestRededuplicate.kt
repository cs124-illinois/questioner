
import edu.illinois.cs.cs124.stumperd.server.CleaningFailure
import edu.illinois.cs.cs124.stumperd.server.DeduplicateFailure
import edu.illinois.cs.cs124.stumperd.server.IdentifyFailure
import edu.illinois.cs.cs124.stumperd.server.RededuplicateFailure
import edu.illinois.cs.cs124.stumperd.server.Steps
import edu.illinois.cs.cs124.stumperd.server.pipeline
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.toList

class TestRededuplicate : StringSpec() {
    override suspend fun beforeSpec(spec: Spec) {
        testCollections.reset()
    }
    init {
        "it should rededuplicate clean submissions correctly" {
            val results = pipeline(pipelineOptions.copy(limit = 1024, stepLimit = Steps.REDEDUPLICATE.value)).toList()

            results.count { it.exception is IdentifyFailure } shouldBe 1
            results.count { it.exception is DeduplicateFailure } shouldBe 81
            results.count { it.exception is CleaningFailure } shouldBe 10
            results.count { it.exception is RededuplicateFailure } shouldBe 70

            testCollections.doneCollection.successCount shouldBeGreaterThan 0
            testCollections.doneCollection.successCount shouldBe results.count() - 1 - 81 - 10 - 70
            testCollections.doneCollection.failureCount shouldBe 1 + 81 + 10 + 70
            testCollections.doneCollection.failureCountForStep(Steps.IDENTIFY) shouldBe 1
            testCollections.doneCollection.failureCountForStep(Steps.DEDUPLICATE) shouldBe 81
            testCollections.doneCollection.failureCountForStep(Steps.CLEAN) shouldBe 10
            testCollections.doneCollection.failureCountForStep(Steps.REDEDUPLICATE) shouldBe 70
        }
    }
}
