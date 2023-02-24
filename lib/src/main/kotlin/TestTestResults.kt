package edu.illinois.cs.cs125.questioner.lib

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CheckstyleResults
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.KtLintResults
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult

@JsonClass(generateAdapter = true)
data class TestTestResults(
    var language: Question.Language,
    val completedSteps: MutableSet<Step> = mutableSetOf(),
    val complete: CompletedTasks = CompletedTasks(),
    val failedSteps: MutableSet<Step> = mutableSetOf(),
    val failed: FailedTasks = FailedTasks(),
    val skippedSteps: MutableSet<Step> = mutableSetOf(),
    var timeout: Boolean = false,
    @Transient
    var taskResults: Sandbox.TaskResults<*>? = null
) {
    var completed: Boolean = false
    var succeeded: Boolean = false
    var failedLinting: Boolean? = null

    @Suppress("EnumNaming", "EnumEntryName")
    enum class Step {
        templateSubmission,
        compileSubmission,
        checkstyle,
        ktlint,
        checkCompiledSubmission,
        testTesting
    }

    @JsonClass(generateAdapter = true)
    data class CompletedTasks(
        // templateSubmission doesn't complete
        var compileSubmission: CompiledSourceResult? = null,
        var checkstyle: CheckstyleResults? = null,
        var ktlint: KtLintResults? = null,
        // checkCompiledSubmission doesn't complete
        var testTesting: TestTestingResults? = null
    )

    @JsonClass(generateAdapter = true)
    data class FailedTasks(
        var templateSubmission: TemplatingFailed? = null,
        var compileSubmission: CompilationFailed? = null,
        var checkstyle: CheckstyleFailed? = null,
        var ktlint: KtLintFailed? = null,
        var checkCompiledSubmission: String? = null,
    )

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

    fun addTestTestingResults(testTesting: TestTestingResults) {
        completedSteps.add(Step.testTesting)
        complete.testTesting = testTesting
    }

    @Suppress("unused")
    fun toJson(): String = moshi.adapter(TestTestResults::class.java).toJson(this)

    @JsonClass(generateAdapter = true)
    data class TestTestingResults(val succeeded: Boolean, val correct: Int, val incorrect: Int, val shortCircuited: Boolean)
}