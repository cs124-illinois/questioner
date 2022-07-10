package edu.illinois.cs.cs125.questioner.lib

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CheckstyleResults
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.ComplexityFailed
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.KtLintResults
import edu.illinois.cs.cs125.jeed.core.LineCounts
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.getStackTraceForSource
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult
import edu.illinois.cs.cs125.jenisol.core.TestResult
import edu.illinois.cs.cs125.jenisol.core.safePrint
import edu.illinois.cs.cs125.jenisol.core.TestResult as JenisolTestResult

@JsonClass(generateAdapter = true)
data class TestResults(
    var language: Question.Language,
    val completedSteps: MutableSet<Step> = mutableSetOf(),
    val complete: CompletedTasks = CompletedTasks(),
    val failedSteps: MutableSet<Step> = mutableSetOf(),
    val failed: FailedTasks = FailedTasks(),
    val skippedSteps: MutableSet<Step> = mutableSetOf(),
    var timeout: Boolean = false,
    @Transient
    var taskResults: Sandbox.TaskResults<*>? = null,
    @Transient
    var resourceMonitoringResults: ResourceMonitoringResults? = null,
    @Transient
    var foundRecursiveMethods: Set<ResourceMonitoringResults.MethodInfo>? = null
) {
    var completed: Boolean = false
    var succeeded: Boolean = false
    var failedLinting: Boolean? = null
    var failureCount: Int? = null

    fun tests() = complete.testing?.tests

    @Suppress("EnumNaming", "EnumEntryName")
    enum class Step {
        templateSubmission,
        compileSubmission,
        checkstyle,
        ktlint,
        checkCompiledSubmission,
        complexity,
        lineCount,

        // execution
        checkExecutedSubmission,
        executioncount,
        memoryAllocation,
        testing,
        coverage,
    }

    @JsonClass(generateAdapter = true)
    data class CompletedTasks(
        // templateSubmission doesn't complete
        var compileSubmission: CompiledSourceResult? = null,
        var checkstyle: CheckstyleResults? = null,
        var ktlint: KtLintResults? = null,
        // checkCompiledSubmission doesn't complete
        var complexity: ComplexityComparison? = null,
        var lineCount: LineCountComparison? = null,
        // execution
        // checkExecutedSubmission doesn't complete
        var executionCount: ResourceUsageComparison? = null,
        var memoryAllocation: ResourceUsageComparison? = null,
        var testing: TestingResult? = null,
        var coverage: CoverageComparison? = null
    )

    fun checkAll() {
        check(failed.checkCompiledSubmission == null) { failed.checkCompiledSubmission!! }
        check(failed.checkExecutedSubmission == null) { failed.checkExecutedSubmission!! }
        check(failedSteps.isEmpty()) { "Failed steps: ${failedSteps.joinToString("\n")}" }
        check(succeeded) { "Testing failed" }
        check(failedLinting != true) { "Linting failed" }
        check(complete.complexity?.failed == false)
        check(complete.lineCount?.failed == false)
        check(complete.executionCount?.failed == false)
        check(complete.memoryAllocation?.failed == false)
        check(complete.coverage?.failed == false)
    }

    @JsonClass(generateAdapter = true)
    data class FailedTasks(
        var templateSubmission: TemplatingFailed? = null,
        var compileSubmission: CompilationFailed? = null,
        var checkstyle: CheckstyleFailed? = null,
        var ktlint: KtLintFailed? = null,
        var checkCompiledSubmission: String? = null,
        var complexity: ComplexityFailed? = null,
        // lineCount doesn't fail
        // execution
        var checkExecutedSubmission: String? = null
        // executionCount doesn't fail
        // memoryAllocation doesn't fail
        // testing doesn't fail
        // coverage doesn't fail
    )

    @JsonClass(generateAdapter = true)
    data class ComplexityComparison(
        val solution: Int,
        val submission: Int,
        val limit: Int,
        val increase: Int = submission - solution,
        val failed: Boolean = increase > limit
    )

    @JsonClass(generateAdapter = true)
    data class LineCountComparison(
        val solution: LineCounts,
        val submission: LineCounts,
        val limit: Int,
        val allowance: Int,
        val increase: Int = submission.source - solution.source,
        val failed: Boolean = increase > allowance && submission.source > limit
    )

    @JsonClass(generateAdapter = true)
    data class CoverageComparison(
        val solution: LineCoverage,
        val submission: LineCoverage,
        val missed: List<Int>,
        val limit: Int,
        val increase: Int = submission.missed - solution.missed,
        val failed: Boolean = increase > limit
    ) {
        @JsonClass(generateAdapter = true)
        data class LineCoverage(val covered: Int, val total: Int, val missed: Int = total - covered) {
            init {
                check(covered <= total) { "Invalid coverage result" }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class ResourceUsageComparison(
        val solution: Long,
        val submission: Long,
        val limit: Long,
        val increase: Long = submission - solution,
        val failed: Boolean = submission >= limit
    )

    @JsonClass(generateAdapter = true)
    data class TestingResult(
        val tests: List<TestResult>,
        val testCount: Int,
        val completed: Boolean,
        val failedReceiverGeneration: Boolean,
        val passed: Boolean = completed && tests.none { !it.passed },
    ) {
        @JsonClass(generateAdapter = true)
        data class TestResult(
            val name: String,
            val passed: Boolean,
            val type: JenisolTestResult.Type,
            val runnerID: Int,
            val stepCount: Int,
            val methodCall: String,
            val message: String? = null,
            val arguments: String? = null,
            val expected: String? = null,
            val found: String? = null,
            val explanation: String? = null,
            val output: String? = null,
            val complexity: Int? = null,
            val submissionStackTrace: String? = null,
            @Transient val jenisol: JenisolTestResult<*, *>? = null,
            @Transient val submissionResourceUsage: ResourceMonitoringCheckpoint? = null
        )
    }

    fun addCheckstyleResults(checkstyle: CheckstyleResults) {
        completedSteps.add(Step.checkstyle)
        complete.checkstyle = checkstyle
        failedLinting = checkstyle.errors.isNotEmpty()
    }

    fun addKtlintResults(ktlint: KtLintResults) {
        completedSteps.add(Step.ktlint)
        complete.ktlint = ktlint
        failedLinting = ktlint.errors.isNotEmpty()
    }

    fun addTestingResults(testing: TestingResult) {
        completedSteps.add(Step.testing)
        complete.testing = testing
        completed = testing.tests.size == testing.testCount
        succeeded = !timeout && testing.passed == true && testing.completed == true && testing.tests.size == testing.testCount
        failureCount = testing.tests.filter { !it.passed }.size
    }

    val summary: String
        get() = if (failed.templateSubmission != null) {
            "Templating failed${failed.templateSubmission?.message?.let { ": $it" } ?: ""}"
        } else if (failed.compileSubmission != null) {
            "Compiling submission failed${failed.compileSubmission?.let { ": $it" } ?: ""}"
        } else if (failed.checkstyle != null) {
            "Checkstyle failed:${failed.checkstyle?.let { ": $it" } ?: ""}"
        } else if (failed.ktlint != null) {
            "Ktlint failed:${failed.ktlint?.let { ": $it" } ?: ""}"
        } else if (failed.complexity != null) {
            "Computing complexity failed: ${failed.complexity!!.message ?: "unknown error"}"
        } else if (failed.checkCompiledSubmission != null) {
            "Checking submission failed: ${failed.checkCompiledSubmission}"
        } else if (failed.checkExecutedSubmission != null) {
            "Checking submission failed: ${failed.checkExecutedSubmission}"
        } else if (timeout) {
            "Testing timed out"
        } else if (complete.testing?.passed == false) {
            "Testing failed: ${complete.testing!!.tests.find { !it.passed }!!.explanation}"
        } else if (complete.testing?.failedReceiverGeneration == true) {
            "Couldn't generate enough receivers"
        } else if (!completed) {
            "Didn't complete all required tests: $failedSteps"
        } else {
            check(succeeded)
            "Passed"
        }

    fun toJson(): String = moshi.adapter(TestResults::class.java).toJson(this)
}

fun TestResult<*, *>.asTestResult(source: Source) = TestResults.TestingResult.TestResult(
    solutionExecutable.name,
    succeeded,
    type,
    runnerID,
    stepCount,
    methodCall(),
    verifierThrew?.message,
    parameters.toString(),
    @Suppress("TooGenericExceptionCaught")
    solution.returned?.safePrint(),
    submission.returned?.safePrint(),
    if (!succeeded) {
        explain()
    } else {
        null
    },
    submission.stdout,
    complexity,
    submission.threw?.getStackTraceForSource(
        source,
        boundaries = listOf(
            "at edu.illinois.cs.cs125.jenisol.core.TestRunner",
            "at jdk.internal.reflect.",
            "at java.base"
        )
    ),
    this,
    this.submission.tag as ResourceMonitoringCheckpoint
)
