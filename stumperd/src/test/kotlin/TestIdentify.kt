import edu.illinois.cs.cs124.stumperd.server.IdentifyFailure
import edu.illinois.cs.cs124.stumperd.server.Steps
import edu.illinois.cs.cs124.stumperd.server.pipeline
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.toList

class TestIdentify : StringSpec() {
    override suspend fun beforeSpec(spec: Spec) {
        testCollections.reset()
    }
    init {
        "it should identify most questions correctly" {
            val results = pipeline(pipelineOptions.copy(stepLimit = Steps.IDENTIFY.value)).toList()

            results.count { it.exception is IdentifyFailure } shouldBe 1

            testCollections.doneCollection.successCount shouldBeGreaterThan 0
            testCollections.doneCollection.successCount shouldBe results.count() - 1
            testCollections.doneCollection.failureCount shouldBe 1
            testCollections.doneCollection.failureCountForStep(Steps.IDENTIFY) shouldBe 1
        }
    }
}
