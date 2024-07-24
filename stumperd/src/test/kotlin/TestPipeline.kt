
import edu.illinois.cs.cs124.stumperd.server.numberPipeline
import io.kotest.core.spec.style.StringSpec

class TestPipeline : StringSpec() {
    init {
        "it should run a pipeline" {
            numberPipeline()
        }
    }
}