import edu.illinois.cs.cs124.stumperd.Stumper
import edu.illinois.cs.cs124.stumperd.pipeline
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.toList

private val fullCounts = FailureCounts(3810, 1, 395)

private val cleanCounts = FailureCounts(1024, 1, 81, 10, 70)

private val validateCounts = FailureCounts(256, 0, 51, 0, 55, 58, 0, 0, 0)

class TestPipeline :
    StringSpec({
        beforeTest {
            testCollections.reset()
        }
        "it should validate mutants correctly" {
            val stepLimit = Stumper.Steps.VALIDATE_MUTANTS
            val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe validateCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)

            results.filter { !it.failed }.sumOf { it.mutants.size } shouldBe 2076
        }
        "it should deduplicate mutants correctly" {
            val stepLimit = Stumper.Steps.DEDUPLICATE_MUTANTS
            val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe validateCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)

            results.filter { !it.failed }.sumOf { it.mutants.size } shouldBe 2127
        }
        "it should mutate submissions correctly" {
            val stepLimit = Stumper.Steps.MUTATE
            val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe validateCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)

            results.filter { !it.failed }.sumOf { it.mutants.size } shouldBe 2134
        }
        "it should validate submissions correctly" {
            val stepLimit = Stumper.Steps.VALIDATE
            val results = pipeline(pipelineOptions.copy(limit = 256, stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe validateCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe validateCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe validateCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe validateCounts.failureCount(stepLimit)
        }
        "it should rededuplicate clean submissions correctly" {
            val stepLimit = Stumper.Steps.REDEDUPLICATE
            val results = pipeline(pipelineOptions.copy(limit = 1024, stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe cleanCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe cleanCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe cleanCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe cleanCounts.failureCount(stepLimit)
        }
        "it should clean submissions correctly" {
            val stepLimit = Stumper.Steps.CLEAN
            val results = pipeline(pipelineOptions.copy(limit = 1024, stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe cleanCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe cleanCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe cleanCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe cleanCounts.failureCount(stepLimit)
        }
        "it should deduplicate submissions correctly" {
            val stepLimit = Stumper.Steps.DEDUPLICATE
            val results = pipeline(pipelineOptions.copy(stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe fullCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe fullCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe fullCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe fullCounts.failureCount(stepLimit)
        }
        "it should identify most questions correctly" {
            val stepLimit = Stumper.Steps.IDENTIFY
            val results = pipeline(pipelineOptions.copy(stepLimit = stepLimit)).toList()

            for (step in Stumper.Steps.forLimit(stepLimit)) {
                results.count { it.failedStep == step } shouldBe fullCounts.countForStep(step)
                testCollections.doneCollection.failureCountForStep(step) shouldBe fullCounts.countForStep(step)
            }

            testCollections.doneCollection.successCount shouldBe fullCounts.successCount(stepLimit)
            testCollections.doneCollection.failureCount shouldBe fullCounts.failureCount(stepLimit)
        }
        "it should run an empty pipeline properly" {
            val results = pipeline(pipelineOptions.copy(stepLimit = Stumper.Steps.NONE)).toList()

            results.count() shouldBe fullCounts.correctCount

            testCollections.doneCollection.successCount shouldBe fullCounts.correctCount
            testCollections.doneCollection.failureCount shouldBe 0
        }
    })
