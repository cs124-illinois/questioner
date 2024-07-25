import edu.illinois.cs.cs124.stumperd.FailureCounts
import edu.illinois.cs.cs124.stumperd.Steps
import edu.illinois.cs.cs124.stumperd.pipeline
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.channels.toList

private val fullCounts = FailureCounts(3810, 1, 395)

private val cleanCounts = FailureCounts(1024, 1, 81, 10, 70)

private val validateCounts = FailureCounts(256, 0, 51, 0, 55, 58, 0, 0, 0)

class TestPipeline : StringSpec({
    beforeTest {
        testCollections.reset()
    }
    "f: it should validate mutants correctly" {
        val stepLimit = Steps.VALIDATE_MUTANTS.value
        val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                validateCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)
    }
    "it should deduplicate mutants correctly" {
        val stepLimit = Steps.DEDUPLICATE_MUTANTS.value
        val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                validateCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)
    }
    "it should mutate submissions correctly" {
        val stepLimit = Steps.MUTATE.value
        val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                validateCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)
    }
    "it should validate submissions correctly" {
        val stepLimit = Steps.VALIDATE.value
        val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                validateCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)
    }
    "it should rededuplicate clean submissions correctly" {
        val stepLimit = Steps.REDEDUPLICATE.value
        val results = pipeline(pipelineOptions.copy(limit = 1024, stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                cleanCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe cleanCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe cleanCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe cleanCounts.failureCount(stepLimit)
    }
    "it should clean submissions correctly" {
        val stepLimit = Steps.CLEAN.value
        val results = pipeline(pipelineOptions.copy(limit = 1024, stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                cleanCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe cleanCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe cleanCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe cleanCounts.failureCount(stepLimit)
    }
    "it should deduplicate submissions correctly" {
        val stepLimit = Steps.DEDUPLICATE.value
        val results = pipeline(pipelineOptions.copy(stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                fullCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe fullCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe fullCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe fullCounts.failureCount(stepLimit)
    }
    "it should identify most questions correctly" {
        val stepLimit = Steps.IDENTIFY.value
        val results = pipeline(pipelineOptions.copy(stepLimit = stepLimit)).toList()

        for (step in Steps.forLimit(stepLimit)) {
            results.count { it.exception?.instanceOf(step.toException()) == true } shouldBe
                fullCounts.countForStep(step)
            testCollections.doneCollection.failureCountForStep(step) shouldBe fullCounts.countForStep(step)
        }

        testCollections.doneCollection.successCount shouldBe fullCounts.successCount(stepLimit)
        testCollections.doneCollection.failureCount shouldBe fullCounts.failureCount(stepLimit)
    }
    "it should run an empty pipeline properly" {
        val results = pipeline(pipelineOptions.copy(stepLimit = 0)).toList()

        results.count() shouldBe fullCounts.correctCount

        testCollections.doneCollection.successCount shouldBe fullCounts.correctCount
        testCollections.doneCollection.failureCount shouldBe 0
    }
})

