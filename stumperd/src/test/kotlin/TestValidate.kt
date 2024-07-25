
import edu.illinois.cs.cs124.stumperd.server.CleaningFailure
import edu.illinois.cs.cs124.stumperd.server.DeduplicateFailure
import edu.illinois.cs.cs124.stumperd.server.IdentifyFailure
import edu.illinois.cs.cs124.stumperd.server.RededuplicateFailure
import edu.illinois.cs.cs124.stumperd.server.Steps
import edu.illinois.cs.cs124.stumperd.server.ValidationFailure
import edu.illinois.cs.cs124.stumperd.server.pipeline
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.toList

class TestValidate : StringSpec() {
    override suspend fun beforeSpec(spec: Spec) {
        testCollections.reset()
    }
    init {
        "it should validate submissions correctly" {
            val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = Steps.VALIDATE.value)).toList()

            results.count { it.exception is IdentifyFailure } shouldBe 0
            results.count { it.exception is DeduplicateFailure } shouldBe 51
            results.count { it.exception is CleaningFailure } shouldBe 0
            results.count { it.exception is RededuplicateFailure } shouldBe 55
            results.count { it.exception is ValidationFailure } shouldBe 58

            testCollections.doneCollection.successCount shouldBeGreaterThan 0
            testCollections.doneCollection.successCount shouldBe results.count() - 0 - 51 - 0 - 55 - 58
            testCollections.doneCollection.failureCount shouldBe 0 + 51 + 0 + 55 + 58
            testCollections.doneCollection.failureCountForStep(Steps.IDENTIFY) shouldBe 0
            testCollections.doneCollection.failureCountForStep(Steps.DEDUPLICATE) shouldBe 51
            testCollections.doneCollection.failureCountForStep(Steps.CLEAN) shouldBe 0
            testCollections.doneCollection.failureCountForStep(Steps.REDEDUPLICATE) shouldBe 55
            testCollections.doneCollection.failureCountForStep(Steps.VALIDATE) shouldBe 58
        }
    }
}
