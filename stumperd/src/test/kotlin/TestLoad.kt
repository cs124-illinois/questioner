
import edu.illinois.cs.cs124.stumperd.Submission
import edu.illinois.cs.cs124.stumperd.asSequence
import edu.illinois.cs.cs124.stumperd.collection
import edu.illinois.cs.cs124.stumperd.filterResults
import edu.illinois.cs.cs124.stumperd.filterType
import edu.illinois.cs.cs124.stumperd.findSubmissions
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TestLoad : StringSpec() {
    private val allCollection by lazy {
        System.getenv("MONGODB_TESTING").collection("all")
    }

    private val resultsCollection by lazy {
        System.getenv("MONGODB_TESTING").collection("results")
    }

    init {
        "it should load correct submissions with type filtering" {
            val expectedCounts = mapOf(
                Submission.Type.QUALITY to 503,
                Submission.Type.SUCCEEDED_NO_FAILURES to 810,
                Submission.Type.PASSED to 1018,
            )
            allCollection.asSequence().also { sequence ->
                sequence.filterResults().count() shouldBe 24696
                for ((type, expectedCount) in expectedCounts) {
                    sequence.filterResults().filterType(type).count() shouldBe expectedCount
                }
            }
            allCollection.findSubmissions().count() shouldBe expectedCounts.values.sum()
        }
        "it should load correct submissions with only results" {
            val expectedCounts = mapOf(
                Submission.Type.QUALITY to 1077,
                Submission.Type.SUCCEEDED_NO_FAILURES to 1714,
                Submission.Type.PASSED to 1019,
            )
            resultsCollection.asSequence().also { sequence ->
                sequence.filterResults().count() shouldBe 40960
                for ((type, expectedCount) in expectedCounts) {
                    sequence.filterResults().filterType(type).count() shouldBe expectedCount
                }
            }
            resultsCollection.findSubmissions().count() shouldBe expectedCounts.values.sum()
        }
    }
}
