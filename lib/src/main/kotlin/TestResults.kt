package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CheckstyleResults
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.KtLintResults
import edu.illinois.cs.cs125.jeed.core.LineCounts
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.getStackTraceForSource
import edu.illinois.cs.cs125.jeed.core.serializers.CheckstyleFailedSerializer
import edu.illinois.cs.cs125.jeed.core.serializers.CompilationFailedSerializer
import edu.illinois.cs.cs125.jeed.core.serializers.KtLintFailedSerializer
import edu.illinois.cs.cs125.jeed.core.serializers.TemplatingFailedSerializer
import edu.illinois.cs.cs125.jeed.core.server.CompiledSourceResult
import edu.illinois.cs.cs125.jenisol.core.TestResult
import edu.illinois.cs.cs125.jenisol.core.safePrint
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import edu.illinois.cs.cs125.jenisol.core.TestResult as JenisolTestResult

@Serializable
data class TestResults(
    var language: Language,
    val completedSteps: MutableSet<Step> = mutableSetOf(),
    val complete: CompletedTasks = CompletedTasks(),
    val failedSteps: MutableSet<Step> = mutableSetOf(),
    val failed: FailedTasks = FailedTasks(),
    val skippedSteps: MutableSet<Step> = mutableSetOf(),
    var timeout: Boolean = false,
    var lineCountTimeout: Boolean = false,
    @Transient
    var taskResults: Sandbox.TaskResults<*>? = null,
    @Transient
    var resourceMonitoringResults: ResourceMonitoringResults? = null,
    @Transient
    var foundRecursiveMethods: Set<ResourceMonitoringResults.MethodInfo>? = null,
    @Transient
    var timings: Timings? = null,
    @Transient
    var jenisolResults: edu.illinois.cs.cs125.jenisol.core.TestResults? = null
) {
    @Suppress("unused")
    val kind = Submission.SubmissionType.SOLVE

    var completed: Boolean = false
    var succeeded: Boolean = false
    var failedLinting: Boolean? = null
    var failureCount: Int? = null

    fun tests() = complete.testing?.tests

    @Suppress("EnumNaming", "EnumEntryName")
    enum class Step {
        checkInitialSubmission,
        templateSubmission,
        compileSubmission,
        checkstyle,
        ktlint,
        checkCompiledSubmission,
        classSize,
        complexity,
        features,
        lineCount,
        partial,

        // execution
        checkExecutedSubmission,
        recursion,
        executioncount,
        memoryAllocation,
        testing,
        coverage,
        extraOutput
    }

    @Serializable
    data class CompletedTasks(
        // templateSubmission doesn't complete
        var compileSubmission: CompiledSourceResult? = null,
        var checkstyle: CheckstyleResults? = null,
        var ktlint: KtLintResults? = null,
        var classSize: ResourceUsageComparison? = null,
        // checkCompiledSubmission doesn't complete
        var complexity: ComplexityComparison? = null,
        var features: FeaturesComparison? = null,
        var lineCount: LineCountComparison? = null,
        var partial: PartialCredit? = null,
        // execution
        // checkExecutedSubmission doesn't complete
        var recursion: RecursionComparison? = null,
        var executionCount: ResourceUsageComparison? = null,
        var memoryAllocation: ResourceUsageComparison? = null,
        // Temporary: individual allocation records for debugging memory discrepancies
        var submissionAllocationRecords: List<AllocationRecord>? = null,
        // Temporary: memory component breakdown for debugging
        var memoryBreakdown: MemoryBreakdown? = null,
        var testing: TestingResult? = null,
        var coverage: CoverageComparison? = null,
        var extraOutput: OutputComparison? = null
    )

    fun checkAll() {
        check(failed.checkInitialSubmission == null) { failed.checkInitialSubmission!! }
        check(failed.checkCompiledSubmission == null) { failed.checkCompiledSubmission!! }
        check(failed.checkExecutedSubmission == null) { failed.checkExecutedSubmission!! }
        check(failedSteps.isEmpty()) { "Failed steps: ${failedSteps.joinToString("\n")}" }
        check(succeeded) { "Testing failed" }
        check(failedLinting != true) { "Linting failed" }
        check(complete.complexity?.failed == false)
        check(complete.features?.failed == false)
        check(complete.lineCount?.failed == false)
        check(complete.recursion?.failed == false)
        check(complete.executionCount?.failed == false)
        check(complete.memoryAllocation?.failed == false)
        check(complete.coverage?.failed == false)
        check(complete.classSize?.failed == false)
        check(complete.extraOutput?.failed == false)
    }

    @Serializable
    data class FailedTasks(
        var checkInitialSubmission: String? = null,
        @Serializable(with = TemplatingFailedSerializer::class) var templateSubmission: TemplatingFailed? = null,
        @Serializable(with = CompilationFailedSerializer::class) var compileSubmission: CompilationFailed? = null,
        @Serializable(with = CheckstyleFailedSerializer::class) var checkstyle: CheckstyleFailed? = null,
        @Serializable(with = KtLintFailedSerializer::class) var ktlint: KtLintFailed? = null,
        var checkCompiledSubmission: String? = null,
        var classSize: String? = null,
        var complexity: String? = null,
        var features: String? = null,
        var lineCount: String? = null,
        // execution
        var checkExecutedSubmission: String? = null
        // executionCount doesn't fail
        // memoryAllocation doesn't fail
        // testing doesn't fail
        // coverage doesn't fail
    )

    @Serializable
    data class ComplexityComparison(
        val solution: Int,
        val submission: Int,
        val limit: Int,
        val increase: Int = submission - solution,
        val failed: Boolean = increase > limit
    )

    @Serializable
    data class LineCountComparison(
        val solution: LineCounts,
        val submission: LineCounts,
        val limit: Int,
        val allowance: Int,
        val increase: Int = submission.source - solution.source,
        val failed: Boolean = increase > allowance && submission.source > limit
    )

    @Serializable
    data class CoverageComparison(
        val solution: LineCoverage,
        val submission: LineCoverage,
        val missed: List<Int>,
        val limit: Int,
        val increase: Int = submission.missed - solution.missed,
        val failed: Boolean = increase > limit
    ) {
        @Serializable
        data class LineCoverage(val covered: Int, val total: Int, val missed: Int = total - covered) {
            init {
                check(covered <= total) { "Invalid coverage result" }
            }
        }
    }

    @Serializable
    data class OutputComparison(
        val solution: Int,
        val submission: Int,
        val truncated: Boolean,
        val failed: Boolean = truncated || (solution == 0 && submission > 0)
    ) {
        init {
            require(solution >= 0) { "Invalid solution output amount: $solution" }
            require(submission >= 0) { "Invalid submission resource usage: $submission" }
        }
    }

    @Serializable
    data class ResourceUsageComparison(
        val solution: Long,
        val submission: Long,
        val limit: Long,
        val increase: Long = submission - solution,
        val failed: Boolean = submission >= limit
    ) {
        init {
            require(solution >= 0) { "Invalid solution resource usage: $solution" }
            require(submission >= 0) { "Invalid submission resource usage: $submission" }
            require(limit >= 0) { "Invalid resource limit: $limit" }
        }
    }

    // Temporary: breakdown of memory components for debugging
    @Serializable
    data class MemoryBreakdown(
        val heapAllocatedMemory: Long,
        val maxCallStackSize: Long,
        val warmupMemory: Long,
        val warmupCount: Int,
        val totalWithStack: Long = heapAllocatedMemory + maxCallStackSize,
        val totalWithWarmup: Long = heapAllocatedMemory + maxCallStackSize + warmupMemory
    )

    @Serializable
    data class FeaturesComparison(
        val errors: List<String>,
        val failed: Boolean = errors.isNotEmpty()
    )

    @Serializable
    data class RecursionComparison(val missingMethods: List<String>, val failed: Boolean = missingMethods.isNotEmpty())

    @Serializable
    data class PartialCredit(
        val passedSteps: PassedSteps = PassedSteps(),
        var passedTestCount: PassedTestCount? = null,
        var passedMutantCount: PassedMutantCount? = null
    ) {
        @Serializable
        data class PassedSteps(
            var compiled: Boolean = false,
            var design: Boolean = false,
            var partiallyCorrect: Boolean = false,
            var fullyCorrect: Boolean = false,
            var quality: Boolean = false
        )

        @Serializable
        data class PassedTestCount(val passed: Int, val total: Int, val completed: Boolean)

        @Serializable
        data class PassedMutantCount(val passed: Int, val total: Int, val completed: Boolean)
    }

    @Serializable
    data class TestingResult(
        val tests: List<TestResult>,
        val testCount: Int,
        val completed: Boolean,
        val failedReceiverGeneration: Boolean,
        @Transient
        val jenisolResults: edu.illinois.cs.cs125.jenisol.core.TestResults? = null,
        val passed: Boolean = completed && tests.none { !it.passed },
        val outputAmount: Int = tests.sumOf { it.outputAmount },
        val truncatedLines: Int = tests.sumOf { it.truncatedLines ?: 0 }
    ) {
        @Serializable
        data class TestResult(
            val name: String,
            val passed: Boolean,
            val type: JenisolTestResult.Type,
            val runnerID: Int,
            val stepCount: Int,
            val methodCall: String,
            val differs: Set<TestResult.Differs>,
            val message: String? = null,
            val arguments: String? = null,
            val expected: String? = null,
            val found: String? = null,
            val explanation: String? = null,
            val output: String? = null,
            val complexity: Int? = null,
            val submissionStackTrace: String? = null,
            val stdin: String? = null,
            val truncatedLines: Int? = null,
            @Transient val jenisol: JenisolTestResult<*, *>? = null,
            @Transient val submissionResourceUsage: ResourceMonitoringCheckpoint? = null,
            val outputAmount: Int = when {
                output == null -> 0
                output.isEmpty() -> 0
                else -> output.lines().size
            }
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
        succeeded =
            !timeout && testing.passed == true && testing.completed == true && testing.tests.size == testing.testCount
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
            "Computing complexity failed: ${failed.complexity ?: "unknown error"}"
        } else if (failed.classSize != null) {
            "Computing class size failed: ${failed.classSize ?: "unknown error"}"
        } else if (failed.checkCompiledSubmission != null) {
            "Checking submission failed: ${failed.checkCompiledSubmission}"
        } else if (failed.checkExecutedSubmission != null) {
            "Checking submission failed: ${failed.checkExecutedSubmission}"
        } else if (failed.features != null) {
            "Checking submission features failed: ${failed.features}"
        } else if (failed.lineCount != null) {
            "Submission executed too many lines: ${failed.lineCount}"
        } else if (timeout && !taskResults!!.cpuTimeout) {
            "Testing wall clock timeout after ${taskResults!!.executionNanoTime / 1000 / 1000}ms " +
                "(${complete.testing?.tests?.size ?: 0} / ${complete.testing?.testCount ?: 0} tests completed, ${jenisolResults?.loopCount ?: 0} loop count)"
        } else if (timeout && taskResults!!.cpuTimeout) {
            "Testing CPU timeout after ${taskResults!!.cpuTime / 1000 / 1000}ms (${complete.testing?.tests?.size ?: 0} tests completed, ${jenisolResults?.loopCount ?: 0} loop count)"
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

    fun toJson(): String = json.encodeToString(this)

    @Transient
    val canCache = !(timeout && !lineCountTimeout)

    data class Timings(val executionTimeNanos: Long, val totalTestTimeNanos: Long) {
        val testingTimePercent = totalTestTimeNanos.toDouble() / executionTimeNanos.toDouble()

        init {
            check(testingTimePercent in 0.0..<1.0) { "Bad value for testingTimePercent: $testingTimePercent" }
        }
    }
}

fun TestResult<*, *>.asTestResult(source: Source, questionType: Question.Type) = TestResults.TestingResult.TestResult(
    solutionExecutable.name,
    succeeded,
    type,
    runnerID,
    stepCount,
    submissionMethodString,
    differs,
    verifierThrew?.message,
    parameters.toString(),
    @Suppress("TooGenericExceptionCaught")
    solution.returned?.safePrint(),
    submission.returned?.safePrint(),
    if (!succeeded) {
        explain(omitMethodName = questionType == Question.Type.SNIPPET)
    } else {
        null
    },
    submission.interleavedOutput.trim() + if (submission.truncatedLines > 0) {
        "\n(${submission.truncatedLines} lines truncated)\n"
    } else {
        ""
    },
    complexity,
    try {
        submission.threw?.getStackTraceForSource(
            source,
            boundaries = listOf(
                "at edu.illinois.cs.cs125.jenisol.core.TestRunner",
                "at jdk.internal.reflect.",
                "at java.base"
            )
        )
    } catch (e: Exception) {
        "Cannot print stack trace: $e"
    },
    submission.stdin,
    submission.truncatedLines,
    this,
    this.submission.tag as ResourceMonitoringCheckpoint
)
