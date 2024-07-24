
import edu.illinois.cs.cs124.stumperd.server.PipelineOptions
import edu.illinois.cs.cs124.stumperd.server.collection
import edu.illinois.cs.cs124.stumperd.server.pipeline
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.toList

class TestIdentify : StringSpec() {

    private val submissionsCollection by lazy {
        System.getenv("MONGODB_TESTING").collection("results")
    }

    private val questionsCollection by lazy {
        System.getenv("MONGODB_TESTING").collection("questions")
    }

    init {
        "it should identify most questions correctly" {
            val pipelineOptions = PipelineOptions(submissionsCollection, questionsCollection)
            val results = pipeline(pipelineOptions).toList()
            results.count { it.exception != null } shouldBe 1
        }
    }
}