package edu.illinois.cs.cs125.questioner.lib

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CheckstyleResults
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.KtLintResults
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.getStackTraceForSource
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult
import edu.illinois.cs.cs125.jenisol.core.TestResult
import edu.illinois.cs.cs125.jenisol.core.safePrint

@JsonClass(generateAdapter = true)
data class TestResults(
    val type: Type,
    val completedSteps: MutableSet<Step> = mutableSetOf(),
    val complete: CompletedTasks = CompletedTasks(),
    val failedSteps: MutableSet<Step> = mutableSetOf(),
    val failed: FailedTasks = FailedTasks(),
    val skippedSteps: MutableSet<Step> = mutableSetOf(),
    var timeout: Boolean = false,
    @Transient
    var taskResults: Sandbox.TaskResults<*>? = null
) {
    fun tests() = complete.testing?.tests

    @Suppress("EnumNaming", "EnumEntryName")
    enum class Type { jenisol }

    @Suppress("EnumNaming", "EnumEntryName")
    enum class Step {
        templateSubmission,
        checkstyle,
        ktlint,
        compileSubmission,
        checkSubmission,
        complexity,
        test,
    }

    @JsonClass(generateAdapter = true)
    data class CompletedTasks(
        var compileSubmission: CompiledSourceResult? = null,
        var checkstyle: CheckstyleResults? = null,
        var ktlint: KtLintResults? = null,
        var compileTest: CompiledSourceResult? = null,
        var testing: TestingResult? = null,
        var complexity: Question.ComplexityComparison? = null
    )

    @JsonClass(generateAdapter = true)
    data class TestingResult(
        val tests: List<TestResult>,
        val testCount: Int,
        val completed: Boolean,
        val passed: Boolean = completed && tests.none { !it.passed }
    ) {
        @JsonClass(generateAdapter = true)
        data class TestResult(
            val name: String,
            val passed: Boolean,
            val message: String? = null,
            val arguments: String? = null,
            val expected: String? = null,
            val found: String? = null,
            val explanation: String? = null,
            val output: String? = null,
            val complexity: Int? = null,
            @Transient val jenisol: edu.illinois.cs.cs125.jenisol.core.TestResult<*, *>? = null,
            val submissionStackTrace: String? = null
        )
    }

    @JsonClass(generateAdapter = true)
    data class FailedTasks(
        var templateSubmission: TemplatingFailed? = null,
        var compileSubmission: CompilationFailed? = null,
        var checkstyle: CheckstyleFailed? = null,
        var checkSubmission: String? = null,
        var compileTest: CompilationFailed? = null,
        var ktlint: KtLintFailed? = null
    )

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    val completed: Boolean
        get() = completedSteps.contains(Step.test)
    val succeeded: Boolean
        get() = !timeout && complete.testing?.passed == true && complete.testing?.completed == true

    val summary: String
        get() = if (failed.templateSubmission != null) {
            "Templating failed${failed.templateSubmission?.message?.let { ": $it" } ?: ""}"
        } else if (failed.compileSubmission != null) {
            "Compiling submission failed${failed.compileSubmission?.let { ": $it" } ?: ""}"
        } else if (failed.checkstyle != null) {
            "Checkstyle failed:${failed.checkstyle?.let { ": $it" } ?: ""}"
        } else if (failed.ktlint != null) {
            "Ktlint failed:${failed.ktlint?.let { ": $it" } ?: ""}"
        } else if (failed.checkSubmission != null) {
            "Checking submission failed: ${failed.checkSubmission}"
        } else if (failed.compileTest != null) {
            "Compiling test failed: ${failed.compileTest?.message?.let { ": $it" } ?: ""}"
        } else if (timeout) {
            "Testing timed out"
        } else if (complete.testing?.passed == false) {
            "Testing failed: ${complete.testing!!.tests.find { !it.passed }!!.explanation}"
        } else if (!completed) {
            "Didn't complete all required tests"
        } else {
            check(succeeded)
            "Passed"
        }

    fun validate(reason: Question.IncorrectFile.Reason, isMutated: Boolean) {
        when (reason) {
            Question.IncorrectFile.Reason.COMPILE -> require(failed.compileSubmission != null) {
                "Expected submission not to compile"
            }
            Question.IncorrectFile.Reason.CHECKSTYLE -> require(failed.checkstyle != null) {
                "Expected submission to fail checkstyle"
            }
            Question.IncorrectFile.Reason.DESIGN -> require(failed.checkSubmission != null) {
                "Expected submission to fail design"
            }
            Question.IncorrectFile.Reason.TIMEOUT -> require(timeout || !succeeded) {
                "Expected submission to timeout"
            }
            else -> require(isMutated || (!timeout && complete.testing?.passed == false)) {
                "Expected submission to fail tests"
            }
        }
    }

    fun toJson(): String = moshi.adapter(TestResults::class.java).toJson(this)
}

fun TestResult<*, *>.asTestResult(source: Source) = TestResults.TestingResult.TestResult(
    solutionExecutable.name,
    succeeded,
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
    this,
    submission.threw?.getStackTraceForSource(
        source,
        boundaries = listOf(
            "at edu.illinois.cs.cs125.jenisol.core.TestRunner",
            "at jdk.internal.reflect.",
            "at java.base"
        )
    )
)
