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
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import edu.illinois.cs.cs125.questioner.lib.server.Submission

@JsonClass(generateAdapter = true)
data class TestTestResults(
    var language: Language,
    val completedSteps: MutableSet<Step> = mutableSetOf(),
    val complete: CompletedTasks = CompletedTasks(),
    val failedSteps: MutableSet<Step> = mutableSetOf(),
    val failed: FailedTasks = FailedTasks(),
    val skippedSteps: MutableSet<Step> = mutableSetOf(),
    var timeout: Boolean = false,
    var lineCountTimeout: Boolean = false,
    @Transient
    var taskResults: Sandbox.TaskResults<*>? = null
) {
    @Suppress("unused")
    val kind = Submission.SubmissionType.TESTTESTING

    var completed: Boolean = false
    var succeeded: Boolean = false
    var failedLinting: Boolean? = null

    @Suppress("EnumNaming", "EnumEntryName")
    enum class Step {
        checkInitialSubmission,
        templateSubmission,
        compileSubmission,
        checkstyle,
        ktlint,
        checkCompiledSubmission,
        checkExecutedSubmission,
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
        var checkInitialSubmission: String? = null,
        var templateSubmission: TemplatingFailed? = null,
        var compileSubmission: CompilationFailed? = null,
        var checkstyle: CheckstyleFailed? = null,
        var ktlint: KtLintFailed? = null,
        var checkCompiledSubmission: String? = null,
        var checkExecutedSubmission: String? = null
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
        completed = true
        succeeded = testTesting.succeeded
    }

    @Suppress("unused")
    fun toJson(): String = moshi.adapter(TestTestResults::class.java).toJson(this)

    @JsonClass(generateAdapter = true)
    data class TestTestingResults(
        val selectionStrategy: Question.TestTestingSettings.SelectionStrategy,
        val correct: Int,
        val incorrect: Int,
        val identifiedSolution: Boolean?,
        val total: Int,
        val duration: Long,
        val output: List<String>,
        val correctMap: Map<Int, Boolean>,
        val succeeded: Boolean = correct == total && incorrect == 0,
        val shortCircuited: Boolean = correct + incorrect < total
    ) {
        init {
            check(correctMap.size == correct + incorrect)
        }
    }

    @Transient
    val canCache = !(timeout && !lineCountTimeout)
}