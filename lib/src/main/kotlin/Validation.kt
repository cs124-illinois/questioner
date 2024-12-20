@file:Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")

package edu.illinois.cs.cs125.questioner.lib

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import edu.illinois.cs.cs125.jeed.core.suppressionComment
import edu.illinois.cs.cs125.jenisol.core.ParameterGroup
import edu.illinois.cs.cs125.jenisol.core.TestResult
import edu.illinois.cs.cs125.jenisol.core.fullName
import edu.illinois.cs.cs125.jenisol.core.isBoth
import edu.illinois.cs.cs125.jenisol.core.solutionTestingSequence
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.time.Instant
import kotlin.io.path.Path

data class CorrectResults(val incorrect: Question.FlatFile, val results: TestResults)
data class IncorrectResults(val incorrect: Question.IncorrectFile, val results: TestResults)

private val calibrationLimiter = Semaphore(1)

private const val RETRY_THRESHOLD = 0.5
var gradleRootDirectory: String? = null

@Suppress("LongMethod", "ComplexMethod")
suspend fun Question.validate(
    seed: Int,
    maxMutationCount: Int,
    retry: Int = 0,
    verbose: Boolean = false
): ValidationReport {
    fauxStatic = solution.fauxStatic

    val javaClassWhitelist = mutableSetOf<String>().apply { addAll(defaultJavaClassWhitelist) }
    val kotlinClassWhitelist = mutableSetOf<String>().apply { addAll(defaultKotlinClassWhitelist) }

    val javaSolution = getSolution(Language.java)!!
    val kotlinSolution = getSolution(Language.kotlin)

    fun TestResults.badTimeout() = timeout && !lineCountTimeout &&
        ((resourceMonitoringResults?.submissionLines?.toDouble()
            ?: 0.0) / Question.TestingControl.DEFAULT_MAX_EXECUTION_COUNT < RETRY_THRESHOLD)

    fun TestResults.checkCorrect(file: Question.FlatFile, finalChecks: Boolean = false) {
        if (taskResults?.threw != null) {
            throw SolutionTestingThrew(file, taskResults!!.threw!!, taskResults!!.output)
        }
        if (!succeeded) {
            if (failed.checkExecutedSubmission != null) {
                throw SolutionFailed(file, failed.checkExecutedSubmission!!, retry)
            }

            val exception = when {
                complete.testing?.passed == false -> SolutionFailed(file, summary, retry)
                complete.testing?.failedReceiverGeneration == true -> SolutionReceiverGeneration(file, retry)
                else -> {
                    val message = when {
                        failedSteps.contains(TestResults.Step.compileSubmission) -> {
                            val templated = if (getTemplate(file.language) != null) {
                                templateSubmission(file.contents, language)
                            } else {
                                null
                            }
                            """Error compiling solution:
                                        |---
                                        |${file.contents}
                                        |${
                                if (templated != null) {
                                    """
                                            |--- (After templating)
                                            |${templated.contents}
                                            ---
                                            """.trimMargin()
                                } else {
                                    ""
                                }
                            }
                                        |---
                                        |${
                                failed.compileSubmission!!.message ?: failed.compileSubmission!!.errors.joinToString(
                                    "\n"
                                )
                            }
                                    """.trimMargin()

                        }

                        else -> summary
                    }
                    SolutionFailed(file, message, retry)
                }
            }
            if (badTimeout()) {
                throw RetryValidation(exception, true)
            } else {
                throw exception
            }
        }
        if (failedLinting!!) {
            val errors = if (language == Language.java) {
                complete.checkstyle!!.errors.joinToString("\n") { "Line ${it.location.line}: ${it.message}" }
            } else {
                complete.ktlint!!.errors.joinToString("\n") { "Line ${it.location.line}: ${it.message}" }
            }
            throw SolutionFailedLinting(file, errors)
        }
        val solutionThrew = tests()?.filter {
            it.jenisol!!.solution.threw != null
        }?.find {
            val exception = it.jenisol!!.solution.threw!!
            exception !is AssertionError && exception !is IllegalArgumentException && exception !is IllegalStateException
        }
        if (!control.solutionThrows!! && solutionThrew != null) {
            throw SolutionThrew(file, solutionThrew.jenisol!!.solution.threw!!, solutionThrew.jenisol.parameters)
        }
        tests()
            ?.filter {
                it.jenisol!!.solution.threw == null
                    && it.jenisol.parameters.toList().isNotEmpty()
                    && !(it.jenisol.solutionExecutable is Method && (it.jenisol.solutionExecutable as Method).isBoth())
            }?.let { results ->
                val executableReturns = mutableMapOf<Executable, MutableList<Any>>()
                for (result in results) {
                    result.jenisol!!.solution.returned?.let {
                        executableReturns[result.jenisol.solutionExecutable] =
                            executableReturns[result.jenisol.solutionExecutable] ?: mutableListOf()
                        executableReturns[result.jenisol.solutionExecutable]!!.add(result.jenisol.solution.returned!!)
                    }
                }
                executableReturns.forEach { (executable, values) ->
                    if (executable.fullName() == "public boolean equals(java.lang.Object)") {
                        return@forEach
                    }
                    if (executable is Constructor<*> && fauxStatic) {
                        return@forEach
                    }
                    if (values.distinct().size == 1) {
                        throw SolutionLacksEntropy(
                            file,
                            values.size,
                            values.distinct().size,
                            executable,
                            solution.fauxStatic,
                            values.first()
                        )
                    }
                }
            }

        val size = toJson().length
        if (finalChecks && (size > Question.DEFAULT_MAX_OUTPUT_SIZE || complete.testing!!.truncatedLines > 0)) {
            throw TooMuchOutput(file.contents, file.path, size, Question.DEFAULT_MAX_OUTPUT_SIZE, file.language)
        }

        if (finalChecks && (complete.coverage!!.failed)) {
            throw SolutionDeadCode(
                file,
                complete.coverage!!.submission.missed,
                complete.coverage!!.limit,
                complete.coverage!!.missed
            )
        }
        check(failedSteps.isEmpty()) { "Failed steps: $failedSteps" }
        val classWhiteList = when (file.language) {
            Language.java -> javaClassWhitelist
            Language.kotlin -> kotlinClassWhitelist
        }
        val newClasses = taskResults!!.sandboxedClassLoader!!.loadedClasses.filter { klass ->
            !klass.startsWith("edu.illinois.cs.cs125.jeed.core") &&
                !klass.startsWith("java.lang.invoke.MethodHandles")
        }.toMutableSet()
        // HACK HACK: Allow java.util.Set methods when java.util.Map is used
        if (file.language == Language.java && newClasses.contains("java.util.Map")) {
            newClasses += "java.util.Set"
        }
        classWhiteList.addAll(newClasses)
    }

    fun TestResults.checkIncorrect(file: Question.IncorrectFile, mutated: Boolean) {
        if (!mutated && failedLinting == true && !listOf(
                Question.IncorrectFile.Reason.CHECKSTYLE,
                Question.IncorrectFile.Reason.KTLINT
            ).contains(file.reason)
        ) {
            val errors = when (language) {
                Language.java -> complete.checkstyle!!.errors.joinToString("\n") { it.message }
                Language.kotlin -> complete.ktlint!!.errors.joinToString("\n") { it.message }
            }
            throw IncorrectFailedLinting(file, javaSolution, errors)
        }

        if (mutated) {
            if (succeeded) {
                throw RetryValidation(IncorrectPassed(file, javaSolution, this, verbose), badTimeout())
            }
        } else {
            try {
                validate(file.reason)
            } catch (e: Exception) {
                val exception = when (succeeded && file.reason == Question.IncorrectFile.Reason.TEST) {
                    true -> RetryValidation(IncorrectPassed(file, javaSolution, this, verbose), badTimeout())
                    else -> IncorrectWrongReason(file, e.message!!, summary)
                }
                if (badTimeout()) {
                    throw RetryValidation(exception, true)
                } else {
                    throw exception
                }
            }
        }
        if (listOf(Question.IncorrectFile.Reason.TEST, Question.IncorrectFile.Reason.TIMEOUT).contains(file.reason)
            && tests()?.size?.let { it > control.maxTestCount!! } == true
        ) {
            val failingInput = tests()!!.find { !it.passed }?.arguments
            throw IncorrectTooManyTests(file, javaSolution, tests()!!.size, control.maxTestCount!!, failingInput)
        }
        val solutionThrew = tests()?.filter {
            it.jenisol!!.solution.threw != null
        }?.find {
            val exception = it.jenisol!!.solution.threw!!
            exception !is AssertionError
                && exception !is IllegalArgumentException
                && exception !is IllegalStateException
        }
        if (!control.solutionThrows!! && solutionThrew != null) {
            throw SolutionThrew(
                javaSolution,
                solutionThrew.jenisol!!.solution.threw!!,
                solutionThrew.jenisol.parameters
            )
        }
        val size = toJson().length
        if (toJson().length > Question.DEFAULT_MAX_OUTPUT_SIZE) {
            throw TooMuchOutput(file.contents, file.path, size, Question.DEFAULT_MAX_OUTPUT_SIZE, file.language)
        }
        if (failed.checkCompiledSubmission == null && failed.checkExecutedSubmission == null && taskResults?.threw != null) {
            throw IncorrectTestingThrew(file, taskResults!!.threw!!, taskResults!!.output)
        }
    }

    val bootStrapStart = Instant.now()

    // The solution and alternate solutions define what external classes can be used, so they need to be run first
    // Sets javaClassWhitelist and kotlinClassWhitelist
    val minTestCount = control.minTestCount!!.coerceAtMost(solution.maxCount)
    val maxTestCount = control.maxTestCount!!.coerceAtMost(solution.maxCount)

    val allSolutions = listOf(javaSolution) + alternativeSolutions
    val allJavaSolutions = allSolutions.filter { solution -> solution.language === Language.java }
    val allKotlinSolutions = allSolutions.filter { solution -> solution.language === Language.kotlin }

    val solutionDeadCode = Question.LanguagesResourceUsage(
        allJavaSolutions.maxOf { solution -> solution.expectedDeadCount ?: 0 }.toLong(),
        allKotlinSolutions.maxOfOrNull { solution -> solution.expectedDeadCount ?: 0 }?.toLong()
    )

    val bootstrapSettings = Question.TestingSettings(
        seed = seed,
        testCount = maxTestCount,
        outputLimit = Question.UNLIMITED_OUTPUT_LINES, // No line limit
        perTestOutputLimit = Question.UNLIMITED_OUTPUT_LINES, // No per test line limit
        javaWhitelist = null,
        kotlinWhitelist = null,
        shrink = false,
        checkBlacklist = false,
        executionCountLimit = Question.LanguagesResourceUsage.both(Question.TestingControl.DEFAULT_MAX_EXECUTION_COUNT),
        solutionDeadCode = solutionDeadCode,
        suppressions = javaSolution.suppressions,
        kotlinSuppressions = kotlinSolution?.suppressions,
        recordTrace = true
        // No execution count limit
        // No allocation limit
        // No known recursive methods yet
    )

    val firstCorrectResults = allSolutions.map { solution ->
        test(solution.contents, solution.language, bootstrapSettings, isSolution = true)
            .also { testResults ->
                testResults.checkCorrect(solution)
            }
    }

    val bootstrapTrace = firstCorrectResults.first().jenisolResults!!.randomTrace!!

    val solutionJavaRecursiveMethods = firstCorrectResults.getRecursiveMethods(Language.java)
    check(solutionJavaRecursiveMethods != null)
    val solutionKotlinRecursiveMethods = firstCorrectResults.getRecursiveMethods(Language.kotlin)

    val solutionRecursiveMethods = makeLanguageMap(solutionJavaRecursiveMethods, solutionKotlinRecursiveMethods)

    val bootstrapSolutionCoverage = firstCorrectResults
        .mapNotNull { it.complete.coverage }
        .minByOrNull {
            it.solution.covered / it.solution.total
        }!!.solution

    val bootstrapSolutionExecutionCount = firstCorrectResults.setResourceUsage { solution ->
        solution.executionCount
    }

    val bootstrapSolutionAllocation = firstCorrectResults.setResourceUsage { solution ->
        solution.memoryAllocation
    }

    val bootstrapClassSize = firstCorrectResults.setResourceUsage { solution ->
        solution.classSize
    }

    val bootstrapSolutionOutputAmount = firstCorrectResults.maxOf { it.complete.testing?.outputAmount ?: 0 }

    val bootstrapLength = Instant.now().toEpochMilli() - bootStrapStart.toEpochMilli()

    val mutationStart = Instant.now()
    val mutations = mutations(seed, control.maxMutationCount ?: maxMutationCount).also { mutations ->
        if (mutations.size < control.minMutationCount!!) {
            throw TooFewMutations(javaSolution, mutations.size, control.minMutationCount!!)
        }
    }
    val allIncorrect = (incorrectExamples + mutations).also { allIncorrect ->
        if (allIncorrect.isEmpty()) {
            throw NoIncorrect(javaSolution)
        }
        check(allIncorrect.all { incorrect -> incorrect.contents != javaSolution.contents }) {
            "Incorrect solution identical to correct solution"
        }
    }
    val mutationLength = Instant.now().toEpochMilli() - mutationStart.toEpochMilli()

    // Next step is to figure out how many tests to run using mutations and @Incorrect annotations
    // Sets requiredTestCount
    val incorrectStart = Instant.now()
    val incorrectSettings = Question.TestingSettings(
        seed = seed,
        testCount = maxTestCount,
        outputLimit = Question.UNLIMITED_OUTPUT_LINES,
        perTestOutputLimit = Question.UNLIMITED_OUTPUT_LINES,
        javaWhitelist = javaClassWhitelist,
        kotlinWhitelist = kotlinClassWhitelist,
        shrink = false,
        solutionCoverage = bootstrapSolutionCoverage,
        solutionExecutionCount = bootstrapSolutionExecutionCount,
        executionCountLimit = Question.LanguagesResourceUsage.both(
            (control.maxTestCount!! * control.maxExecutionCountMultiplier!!).toLong() * 1024,
        ),
        solutionAllocation = bootstrapSolutionAllocation,
        solutionRecursiveMethods = solutionRecursiveMethods,
        solutionDeadCode = solutionDeadCode,
        solutionClassSize = bootstrapClassSize,
        suppressions = javaSolution.suppressions,
        kotlinSuppressions = kotlinSolution?.suppressions,
        followTrace = bootstrapTrace,
        solutionOutputAmount = bootstrapSolutionOutputAmount
    )
    val incorrectResults = allIncorrect.map { wrong ->
        val specificIncorrectSettings = if (wrong.reason == Question.IncorrectFile.Reason.MEMORYLIMIT) {
            // Fair comparison of total memory usage to bootstrap
            incorrectSettings.copy(testCount = bootstrapSettings.testCount)
        } else {
            incorrectSettings
        }.let { settings ->
            when (wrong.mutation != null) {
                true -> settings
                false -> settings.copy(suppressions = wrong.suppressions)
            }
        }
        test(
            wrong.contents,
            wrong.language,
            specificIncorrectSettings
        ).let { testResults ->
            testResults.checkIncorrect(wrong, wrong.mutation != null)
            IncorrectResults(wrong, testResults)
        }
    }
    val incorrectLength = Instant.now().toEpochMilli() - incorrectStart.toEpochMilli()

    val useTestingIncorrect = incorrectResults.mapIndexed { _, result ->
        when {
            result.results.timeout || (result.results.failureCount ?: 0) == 0 -> null
            else -> result
        }
    }.filterNotNull()

    testTestingIncorrect = useTestingIncorrect.mapIndexed { i, result ->
        val correct = getCorrect(result.incorrect.language)!!.lines()
        val extension = when (result.incorrect.language) {
            Language.java -> ".java"
            Language.kotlin -> ".kt"
        }
        val diffs = DiffUtils.diff(correct, result.incorrect.contents.lines())
        val unifiedDiffs =
            UnifiedDiffUtils.generateUnifiedDiff("Correct$extension", "Incorrect$extension", correct, diffs, 0)
        val incorrectIndex = if (allIncorrect[i] in incorrectExamples) {
            i
        } else {
            null
        }

        // Ignore constructor invocation for faux static methods
        val failureIndex = result.results.tests()!!.filter {
            if (fauxStatic) {
                it.type != TestResult.Type.CONSTRUCTOR
            } else {
                true
            }
        }.indexOfFirst { !it.passed }

        Pair(
            Question.TestTestingMutation(
                unifiedDiffs,
                result.incorrect.language,
                incorrectIndex,
                allIncorrect[i].mutation?.mutations?.first()?.mutation?.mutationType,
                failureIndex,
                result.incorrect.suppressions
            ), result.incorrect.contents
        )
    }.sortedWith(
        compareBy(
            { (validationMutation, _) -> validationMutation.testCount },
            { (_, content) -> content.hashCode() })
    ).map { (validationMutation, _) -> validationMutation }

    val incorrectRequiredTestCount = incorrectResults
        .filter { !it.results.timeout && !it.results.succeeded }
        .mapNotNull { it.results.tests()?.size }
        .maxOrNull() ?: error("No incorrect results")

    val bootstrapRandomStartCount = firstCorrectResults.maxOfOrNull { results ->
        val maxComplexity = results.tests()!!.maxOf { it.complexity!! }
        results.tests()!!.indexOfFirst { it.complexity!! == maxComplexity } + 1
    }!!.coerceAtLeast(0)

    val requiredTestCount = incorrectRequiredTestCount.coerceAtLeast(bootstrapRandomStartCount)

    val testCount = requiredTestCount.coerceAtLeast(minTestCount)
    check(testCount <= control.maxTestCount!!) {
        "Testing requires $testCount tests ($incorrectRequiredTestCount, $bootstrapRandomStartCount) but control class limits to ${control.maxTestCount!!}. Please adjust this limit."
    }

    // Rerun solutions to set timeouts and output limits
    // sets solution runtime, output lines, executed lines, and allocation
    val calibrationStart = Instant.now()
    val calibrationSettings = Question.TestingSettings(
        seed = seed,
        testCount = testCount,
        outputLimit = Question.UNLIMITED_OUTPUT_LINES,
        perTestOutputLimit = Question.UNLIMITED_OUTPUT_LINES,
        javaWhitelist = javaClassWhitelist,
        kotlinWhitelist = kotlinClassWhitelist,
        shrink = false,
        executionCountLimit = Question.LanguagesResourceUsage(
            (testCount * control.maxExecutionCountMultiplier!!).toLong() * 1024,
            (testCount * control.maxExecutionCountMultiplier!!).toLong() * 1024
        ),
        solutionRecursiveMethods = solutionRecursiveMethods,
        solutionDeadCode = solutionDeadCode,
        solutionClassSize = bootstrapClassSize,
        suppressions = javaSolution.suppressions,
        kotlinSuppressions = kotlinSolution?.suppressions,
    )
    val calibrationResults = allSolutions.map { right ->
        val results = calibrationLimiter.withPermit {
            test(right.contents, right.language, calibrationSettings)
        }
        results.checkCorrect(right, true)
        CorrectResults(right, results)
    }

    fun String.filterLoadedClass() = !startsWith("java.lang.invoke.") &&
        !startsWith("edu.illinois.cs.cs125.jeed") &&
        !startsWith("edu.illinois.cs.cs125.questioner") &&
        !startsWith("jdk.internal.") &&
        !startsWith("kotlin.jvm.internal.") &&
        this != "kotlin.Metadata" &&
        this != question.klass &&
        this != "${question.klass}Kt"

    val solutionLoadedClassesJava = calibrationResults
        .asSequence()
        .filter { it.results.language == Language.java }
        .map { it.results.taskResults!!.sandboxedClassLoader!!.loadedClasses }.flatten()
        .filter { it.filterLoadedClass() }.toSet()
    val solutionLoadedClassesKotlin = if (calibrationResults.any { it.results.language == Language.kotlin }) {
        calibrationResults
            .asSequence()
            .filter { it.results.language == Language.kotlin }
            .map { it.results.taskResults!!.sandboxedClassLoader!!.loadedClasses }.flatten()
            .filter { it.filterLoadedClass() }.toSet()
    } else {
        null
    }

    val calibrationLength = Instant.now().toEpochMilli() - calibrationStart.toEpochMilli()

    val solutionMaxRuntime = calibrationResults.maxOf { it.results.taskResults!!.interval.length.toInt() }

    val solutionMaxPerTestOutputLines = calibrationResults.maxOf { results ->
        results.results.tests()!!.maxOf { it.output!!.lines().size }
    }
    val solutionExecutionCounts = calibrationResults.map { it.results }.setResourceUsage { it.executionCount }
    val solutionAllocation = calibrationResults.map { it.results }.setResourceUsage { it.memoryAllocation }
    val solutionCoverage = calibrationResults
        .mapNotNull { it.results.complete.coverage }
        .minByOrNull {
            it.solution.covered / it.solution.total
        }!!.solution
    val solutionOutputAmount =
        calibrationResults.maxOf { it.results.complete.testing?.outputAmount ?: 0 }

    testingSettings = Question.TestingSettings(
        seed = seed,
        testCount = testCount,
        outputLimit = 0, // solutionMaxOutputLines.coerceAtLeast(testCount * control.outputMultiplier!!),
        perTestOutputLimit = (solutionMaxPerTestOutputLines * control.outputMultiplier!!).toInt()
            .coerceAtLeast(Question.MIN_PER_TEST_LINES),
        javaWhitelist = javaClassWhitelist,
        kotlinWhitelist = kotlinClassWhitelist,
        shrink = false,
        executionCountLimit = Question.LanguagesResourceUsage(
            (solutionExecutionCounts.java * control.executionTimeoutMultiplier!!).toLong(),
            (solutionExecutionCounts.kotlin?.times(control.executionTimeoutMultiplier!!)?.toLong())
        ),
        allocationLimit = Question.LanguagesResourceUsage(
            (solutionAllocation.java.toDouble() * control.allocationLimitMultiplier!!).toLong(),
            (solutionAllocation.kotlin?.toDouble()?.times(control.allocationLimitMultiplier!!))?.toLong()
        ),
        solutionRecursiveMethods = solutionRecursiveMethods,
        solutionDeadCode = solutionDeadCode,
        solutionClassSize = bootstrapClassSize,
        suppressions = javaSolution.suppressions,
        kotlinSuppressions = kotlinSolution?.suppressions,
    )

    val incorrectAllocation =
        useTestingIncorrect.map { it.results }.setResourceUsage(bothJava = true) { it.memoryAllocation }

    testTestingLimits = Question.TestTestingLimits(
        outputLimit = (testCount * control.outputMultiplier!!).toInt(),
        executionCountLimit = Question.LanguagesResourceUsage(
            (testCount * control.executionTimeoutMultiplier!!).toLong(),
            (testCount * control.executionTimeoutMultiplier!!).toLong()
        ),
        allocationLimit = Question.LanguagesResourceUsage(
            (incorrectAllocation.java.toDouble() * control.allocationLimitMultiplier!!).toLong(),
            (incorrectAllocation.kotlin?.toDouble()?.times(control.allocationLimitMultiplier!!))?.toLong()
        )
    )

    val canTestTest = control.canTestTest != false &&
        (published.type == Question.Type.METHOD || published.type == Question.Type.KLASS) &&
        !calibrationResults.any { correctResults ->
            correctResults.results.tests()!!
                .any { testResult -> testResult.stdin?.isNotEmpty() == true || testResult.output?.isNotEmpty() == true }
        } &&
        !solution.usesSystemIn && !solution.usesFileSystem

    validationResults = Question.ValidationResults(
        seed = seed,
        requiredTestCount = requiredTestCount,
        mutationCount = mutations.size,
        solutionMaxRuntime = solutionMaxRuntime,
        bootstrapLength = bootstrapLength,
        mutationLength = mutationLength,
        incorrectLength = incorrectLength,
        calibrationLength = calibrationLength,
        solutionCoverage = solutionCoverage,
        executionCounts = solutionExecutionCounts,
        memoryAllocation = solutionAllocation,
        outputAmount = solutionOutputAmount,
        canTestTest = canTestTest
    )

    classification.recursiveMethodsByLanguage = solutionRecursiveMethods!!
    classification.loadedClassesByLanguage = makeLanguageMap(solutionLoadedClassesJava, solutionLoadedClassesKotlin)!!

    val solutionTestingSequence = try {
        calibrationResults.first().results.jenisolResults!!.solutionTestingSequence()
    } catch (e: Exception) {
        null
    }
    return ValidationReport(
        this,
        calibrationResults,
        incorrectResults,
        requiredTestCount,
        solutionMaxRuntime,
        hasKotlin,
        solutionTestingSequence
    )
}

private fun TestResults.validate(reason: Question.IncorrectFile.Reason) {
    when (reason) {
        Question.IncorrectFile.Reason.COMPILE -> require(failed.compileSubmission != null) {
            "Expected submission not to compile"
        }

        Question.IncorrectFile.Reason.CHECKSTYLE -> require(failed.checkstyle != null || complete.checkstyle?.errors?.isNotEmpty() == true) {
            "Expected submission to fail checkstyle"
        }

        Question.IncorrectFile.Reason.KTLINT -> require(failed.ktlint != null || complete.ktlint?.errors?.isNotEmpty() == true) {
            "Expected submission to fail ktlint"
        }

        Question.IncorrectFile.Reason.DESIGN -> require(failed.checkCompiledSubmission != null || failed.checkExecutedSubmission != null) {
            "Expected submission to fail design"
        }

        Question.IncorrectFile.Reason.TIMEOUT -> require(timeout || !succeeded) {
            "Expected submission to timeout"
        }

        Question.IncorrectFile.Reason.DEADCODE -> require(complete.coverage?.failed == true) {
            "Expected submission to contain dead code: ${complete.coverage}"
        }

        Question.IncorrectFile.Reason.LINECOUNT -> require(complete.executionCount?.failed == true) {
            "Expected submission to execute too many lines"
        }

        Question.IncorrectFile.Reason.TOOLONG -> require(complete.lineCount?.failed == true) {
            "Expected submission to contain too many lines"
        }

        Question.IncorrectFile.Reason.MEMORYLIMIT -> require(complete.memoryAllocation?.failed == true) {
            "Expected submission to allocate too much memory: ${complete.memoryAllocation}"
        }

        Question.IncorrectFile.Reason.RECURSION -> require(complete.recursion?.failed == true) {
            "Expected submission to not correctly provide a recursive method"
        }

        Question.IncorrectFile.Reason.COMPLEXITY -> require(complete.complexity?.failed == true) {
            "Expected submission to be too complex"
        }

        Question.IncorrectFile.Reason.FEATURES -> require(failed.features != null) {
            "Expected submission to fail feature check"
        }

        Question.IncorrectFile.Reason.CLASSSIZE -> require(failed.classSize != null) {
            "Expected submission to fail class size check"
        }

        Question.IncorrectFile.Reason.MEMOIZATION -> require(failed.complexity != null && failed.complexity!!.contains("exceeds maximum")) {
            "Expected submission to be so complex as to suggest memoization"
        }

        Question.IncorrectFile.Reason.EXTRAOUTPUT -> require(complete.extraOutput?.failed == true) {
            "Expected submission to generate unnecessary output"
        }

        Question.IncorrectFile.Reason.TEST -> require(complete.testing?.passed == false) {
            "Expected submission to fail tests"
        }
    }
}

data class ValidationReport(
    val question: Question,
    val correct: List<CorrectResults>,
    val incorrect: List<IncorrectResults>,
    val requiredTestCount: Int,
    val requiredTime: Int,
    val hasKotlin: Boolean,
    val solutionTestingSequence: List<String>?
) {
    data class Summary(
        val incorrect: Int,
        val requiredTestCount: Int,
        val requiredTime: Int,
        val kotlin: Boolean
    )

    val summary = Summary(incorrect.size, requiredTestCount, requiredTime, hasKotlin)
}

sealed class ValidationFailed(cause: Exception? = null, val retries: Int = 0) : Exception(cause) {
    fun printContents(contents: String, path: String?) = """
${path?.let { "file://${Path(gradleRootDirectory ?: "/").resolve(path)}\n" } ?: ""}---
$contents
---""".trimStart()
}

class RetryValidation(cause: Exception, val timeout: Boolean) : ValidationFailed(cause)

class SolutionFailed(val solution: Question.FlatFile, val explanation: String, retries: Int) :
    ValidationFailed(retries = retries) {
    override val message = """
        |Solution failed the test suites after $retries retries: $explanation
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class SolutionReceiverGeneration(val solution: Question.FlatFile, retries: Int) : ValidationFailed(retries = retries) {
    override val message = """
        |Solution failed the test suites after $retries retries: Couldn't generate enough receivers during testing.
        |Examine any @FilterParameters methods you might be using, or exceptions thrown in your constructor.
        |Consider adding parameter generation methods for your constructor.
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class SolutionFailedLinting(val solution: Question.FlatFile, val errors: String) : ValidationFailed() {
    override val message = """
        |Solution failed linting with ${
        if (solution.language == Language.kotlin) {
            "ktlint\n$errors"
        } else {
            "checkstyle\n$errors"
        }
    }
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class SolutionThrew(val solution: Question.FlatFile, val threw: Throwable, val parameters: ParameterGroup) :
    ValidationFailed() {
    override val message = """
        |Solution was not expected to throw an unusual exception, but threw $threw on parameters $parameters
        |If it should throw, allow it using @Correct(solutionThrows = true)
        |Otherwise filter the inputs using @FixedParameters, @RandomParameters, or @FilterParameters
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class SolutionTestingThrew(val solution: Question.FlatFile, val threw: Throwable, val output: String = "") :
    ValidationFailed() {
    override val message = """
        |Solution testing threw an exception $threw
        |${threw.stackTraceToString()}${
        if (output.isNotEmpty()) {
            "\n---\n${output.trim()}\n---"
        } else {
            ""
        }
    }
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class IncorrectTestingThrew(val incorrect: Question.IncorrectFile, val threw: Throwable, val output: String = "") :
    ValidationFailed() {
    override val message = """
        |Testing threw an unexpected exception $threw
        |${threw.stackTraceToString()}${
        if (output.isNotEmpty()) {
            "\n---\n${output.trim()}\n---"
        } else {
            ""
        }
    }
        |${printContents(incorrect.contents, incorrect.path)}
        """.trimMargin()
}

class SolutionLacksEntropy(
    val solution: Question.FlatFile,
    val count: Int,
    val amount: Int,
    val executable: Executable,
    val fauxStatic: Boolean,
    val result: Any?
) :
    ValidationFailed() {
    override val message = """
        |$count inputs to the solution method ${executable.fullName()} only generated $amount distinct results: $result
        |${
        if (fauxStatic) {
            "Note that the solution is being tested as a faux static method, which may cause problems"
        } else {
            ""
        }
    }
        |You may need to add or adjust the @RandomParameters method or @FixedParameters field
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class SolutionDeadCode(
    val solution: Question.FlatFile,
    val amount: Int,
    val maximum: Int,
    val dead: List<Int>
) :
    ValidationFailed() {
    override val message = """
        |Solution contains $amount lines of dead code, more than the maximum of $maximum
        |Dead lines: ${dead.joinToString(", ")}
        |You may need to add or adjust the @RandomParameters method
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class NoIncorrect(val solution: Question.FlatFile) : ValidationFailed() {
    override val message = """ No incorrect examples found or generated through mutation
        |Please add some using the @Incorrect annotation
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class TooFewMutations(val solution: Question.FlatFile, val found: Int, val needed: Int) : ValidationFailed() {
    override val message = """ Too few incorrect mutations generated : found $found, needed $needed
        |Please reduce the required number or remove mutation suppressions
        |${printContents(solution.contents, solution.path)}
        """.trimMargin()
}

class TooMuchOutput(
    val contents: String,
    val path: String?,
    val size: Int,
    val maxSize: Int,
    val language: Language
) : ValidationFailed() {
    override val message = """
        |Submission generated too much output($size > $maxSize):
        |Maybe reduce the number of tests using @Correct(minTestCount = NUM)
        |${printContents(contents, path)}
        """.trimMargin()
}

class IncorrectFailedLinting(
    val incorrect: Question.IncorrectFile, val correct: Question.FlatFile, val errors: String
) : ValidationFailed() {
    override val message: String
        get() {
            val contents = incorrect.mutation?.marked()?.contents ?: incorrect.contents
            return """
        |Incorrect code failed linting with ${
                if (incorrect.language == Language.kotlin) {
                    "ktlint\n$errors"
                } else {
                    "checkstyle\n$errors"
                }
            }
        |${printContents(contents, incorrect.path ?: correct.path)}
        """.trimMargin()
        }
}

class IncorrectPassed(
    val incorrect: Question.IncorrectFile,
    val correct: Question.FlatFile,
    val results: TestResults,
    val verbose: Boolean
) : ValidationFailed() {
    override val message: String
        get() {
            val contents = incorrect.mutation?.marked()?.contents ?: incorrect.contents
            return """
        |Incorrect code${
                if (incorrect.mutation != null) {
                    " (mutated) "
                } else {
                    " "
                }
            }passed the test suites:
        |If the code is incorrect, add an input to @FixedParameters to handle this case
        |${
                if (incorrect.mutation != null) {
                    "If the code is correct, you may need to disable this mutation using " +
                        "// ${incorrect.mutation.mutations.first().mutation.mutationType.suppressionComment()}"
                } else {
                    ""
                }
            }
        |${printContents(contents, incorrect.path ?: correct.path)}
        |${
                if (verbose) {
                    results.jenisolResults ?: ""
                } else {
                    ""
                }
            }""".trimMargin().trimEnd()
        }
}

class IncorrectTooManyTests(
    val incorrect: Question.IncorrectFile, val correct: Question.FlatFile,
    val testsRequired: Int, val testsLimit: Int, val failingInput: String?
) : ValidationFailed() {
    override val message: String
        get() {
            val contents = incorrect.mutation?.marked()?.contents ?: incorrect.contents
            return """
        |Incorrect code eventually failed but required too many tests($testsRequired > $testsLimit)
        |${failingInput?.let { "We found failing inputs $failingInput" } ?: "We were unable to find a failing input"}
        |If the code is incorrect, add an input to @FixedParameters to handle this case
        |${
                if (incorrect.mutation != null) {
                    "If the code is correct, you may need to disable this mutation using " +
                        "// ${incorrect.mutation.mutations.first().mutation.mutationType.suppressionComment()}\n"
                } else {
                    ""
                }
            } You may also need to increase the test count using @Correct(maxTestCount = NUM)
        |${printContents(contents, incorrect.path ?: correct.path)}
        """.trimMargin()
        }
}

class IncorrectWrongReason(val incorrect: Question.IncorrectFile, val expected: String, val explanation: String) :
    ValidationFailed() {
    override val message: String
        get() {
            check(incorrect.mutation == null) { "Mutated sources failed for the wrong reason" }
            return """
        |Incorrect code failed but not for the reason we expected :
        |Expected: $expected
        |But Found : $explanation
        |Maybe check the argument to @Incorrect(reason = "reason")
        |${printContents(incorrect.contents, incorrect.path)}
        """.trimMargin()
        }
}

fun List<TestResults>.setResourceUsage(
    multiplier: Double = 1.0,
    bothJava: Boolean = false,
    aspect: (TestResults.CompletedTasks) -> TestResults.ResourceUsageComparison?
): Question.LanguagesResourceUsage {
    val javaValue = filter { it.language == Language.java }
        .mapNotNull { aspect(it.complete) }
        .maxByOrNull {
            it.solution
        }!!.solution.times(multiplier).toLong()
    val kotlinValue = if (bothJava) {
        javaValue
    } else {
        filter { it.language == Language.kotlin }
            .mapNotNull { aspect(it.complete) }
            .maxByOrNull {
                it.solution
            }?.solution?.times(multiplier)?.toLong()
    }
    return Question.LanguagesResourceUsage(javaValue, kotlinValue)
}

private fun List<TestResults>.getRecursiveMethods(language: Language) =
    filter { testResults -> testResults.language == language }
        .let {
            if (it.isEmpty()) {
                null
            } else {
                it.map { testResults -> testResults.foundRecursiveMethods!! }
                    .reduce { first, second ->
                        first.intersect(second)
                    }
            }
        }?.toSet()

