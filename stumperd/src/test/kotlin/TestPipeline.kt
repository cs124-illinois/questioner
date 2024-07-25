
import edu.illinois.cs.cs124.stumperd.server.pipeline
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.toList

class TestPipeline : StringSpec() {
    override suspend fun beforeSpec(spec: Spec) {
        testCollections.reset()
    }
    init {
        "it should run the pipeline properly" {
            val results = pipeline(pipelineOptions.copy(stepLimit = 0)).toList()

            results.count() shouldBe 3810

            testCollections.doneCollection.successCount shouldBe 3810
            testCollections.doneCollection.failureCount shouldBe 0
        }
    }
}
