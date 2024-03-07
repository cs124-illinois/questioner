package edu.illinois.cs.cs125.questioner.lib

import com.beyondgrader.resourceagent.StaticFailureDetection
import com.beyondgrader.resourceagent.jeed.VirtualFilesystem
import com.beyondgrader.resourceagent.jeed.VirtualFilesystemArguments
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.ComplexityFailed
import edu.illinois.cs.cs125.jeed.core.ConfiguredSandboxPlugin
import edu.illinois.cs.cs125.jeed.core.Jacoco
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.LineCoverage
import edu.illinois.cs.cs125.jeed.core.LineLimitExceeded
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.SnippetTransformationFailed
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.UnitFeatures
import edu.illinois.cs.cs125.jeed.core.adjustWithFeatures
import edu.illinois.cs.cs125.jeed.core.features
import edu.illinois.cs.cs125.jeed.core.processCoverage
import edu.illinois.cs.cs125.jenisol.core.Settings
import edu.illinois.cs.cs125.jenisol.core.SubmissionDesignError
import edu.illinois.cs.cs125.jenisol.core.TestResult
import java.lang.reflect.InvocationTargetException

class CachePoisonedException(message: String) : Error(message)

const val MAX_INDIVIDUAL_ALLOCATION_BYTES: Long = 1024 * 1024
private const val MIN_ALLOCATION_FAILURE_BYTES: Long = 2 * 1024 // Account for nondeterminism due to JIT
const val MIN_ALLOCATION_LIMIT_BYTES: Long = 2 * 1024 * 1024 // Leave room for concat in println debugging

@Suppress("ReturnCount", "LongMethod", "ComplexMethod", "LongParameterList", "UNREACHABLE_CODE")
suspend fun Question.test(
    contents: String,
    language: Language,
    settings: Question.TestingSettings = testingSettings!!,
    isSolution: Boolean = false
): TestResults {
    return try {
        warm()

        val results = TestResults(language)

        // initialize partial credit information
        results.complete.partial = TestResults.PartialCredit()
        results.completedSteps.add(TestResults.Step.partial)

        // checkInitialSubmission
        if (!(checkInitialSubmission(contents, language, results))) {
            return results
        }

        // templateSubmission
        val source = contentsToSource(contents, language, results)

        // compileSubmission
        // checkstyle || ktlint
        @Suppress("SwallowedException")
        val compiledSubmission = try {
            when (language) {
                Language.java ->
                    compileSubmission(
                        source,
                        InvertingClassLoader(setOf(published.klass)),
                        results,
                        settings.suppressions ?: setOf()
                    )

                Language.kotlin ->
                    kompileSubmission(
                        source,
                        InvertingClassLoader(setOf(published.klass, "${published.klass}Kt")),
                        results,
                        settings.kotlinSuppressions ?: setOf()
                    )
            }
        } catch (e: TemplatingFailed) {
            return results
        } catch (e: CompilationFailed) {
            return results
        } catch (e: CheckstyleFailed) {
            return results
        } catch (e: KtLintFailed) {
            return results
        }

        results.complete.partial!!.passedSteps.compiled = true

        // checkCompiledSubmission
        val klassName = checkCompiledSubmission(compiledSubmission, results) ?: return results

        // class size
        try {
            results.complete.classSize = computeClassSize(compiledSubmission, language, settings)
            results.completedSteps.add(TestResults.Step.classSize)
        } catch (e: MaxClassSizeExceeded) {
            results.failed.classSize = e.message
            results.failedSteps.add(TestResults.Step.classSize)
            return results
        }

        // complexity
        try {
            results.complete.complexity = computeComplexity(contents, language)
            results.completedSteps.add(TestResults.Step.complexity)
        } catch (e: SnippetTransformationFailed) {
            // Special case when snippet transformation fails
            results.failed.checkCompiledSubmission = "Do not use return statements for this problem"
            results.failedSteps.add(TestResults.Step.checkCompiledSubmission)
            return results
        } catch (e: MaxComplexityExceeded) {
            results.failed.complexity = e.message
            results.failedSteps.add(TestResults.Step.complexity)
            return results
        } catch (e: ComplexityFailed) {
            results.failed.complexity =
                "Unable to compute complexity for this submission:\n" + e.errors.joinToString(", ")
            results.failedSteps.add(TestResults.Step.complexity)
            return results
        }

        // features
        val submissionFeatureResults = try {
            source.features()
        } catch (e: FeatureCheckException) {
            results.failed.features = e.message!!
            results.failedSteps.add(TestResults.Step.features)
            return results
        } catch (e: Exception) {
            results.failed.features = e.message ?: "Unknown features failure"
            results.failedSteps.add(TestResults.Step.features)
            return results
        }

        val submissionFeatures = submissionFeatureResults.lookup("", filename(language))
        check(submissionFeatures is UnitFeatures) { "Invalid submissionFeatures" }

        try {
            results.complete.features = checkFeatures(submissionFeatures.features, language)
            results.completedSteps.add(TestResults.Step.features)
        } catch (e: FeatureCheckException) {
            results.failed.features = e.message!!
            results.failedSteps.add(TestResults.Step.features)
            return results
        } catch (e: Exception) {
            results.failed.features = e.message ?: "Unknown features failure"
            results.failedSteps.add(TestResults.Step.features)
            return results
        }

        // linecount
        try {
            results.complete.lineCount = computeLineCounts(contents, language)
            results.completedSteps.add(TestResults.Step.lineCount)
        } catch (e: MaxLineCountExceeded) {
            results.failed.lineCount = e.message!!
            results.failedSteps.add(TestResults.Step.lineCount)
            return results
        } catch (e: Exception) {
            results.failed.lineCount = e.message ?: "Unknown line count failure"
            results.failedSteps.add(TestResults.Step.lineCount)
            return results
        }

        // execution
        val classLoaderConfiguration = when (language) {
            Language.java -> settings.javaWhitelist
            Language.kotlin -> settings.kotlinWhitelist
        }?.let { whitelistedClasses ->
            Sandbox.ClassLoaderConfiguration(isWhiteList = true, whitelistedClasses = whitelistedClasses)
        } ?: Sandbox.ClassLoaderConfiguration()

        val jenisolSettings = Settings(
            seed = settings.seed,
            shrink = settings.shrink,
            testCount = settings.testCount,
            minTestCount = settings.minTestCount,
            maxTestCount = settings.maxTestCount,
        )
        val systemInStream = BumpingInputStream()

        // Allow giving questions a bit of extra time on first run
        val adjustedTimeout = if (testingCount == 0) {
            settings.timeout + control.initialTestingDelay!!
        } else {
            settings.timeout
        }.toLong()

        val executionArguments = Sandbox.ExecutionArguments(
            timeout = adjustedTimeout,
            classLoaderConfiguration = classLoaderConfiguration,
            maxOutputLines = settings.outputLimit,
            permissions = Question.SAFE_PERMISSIONS,
            returnTimeout = Question.DEFAULT_RETURN_TIMEOUT,
            systemInStream = systemInStream
        )

        val lineCountLimit = when (language) {
            Language.java -> settings.executionCountLimit.java
            Language.kotlin -> settings.executionCountLimit.kotlin!!
        }.takeIf { !settings.disableLineCountLimit }
        val allocationLimit = when (language) {
            Language.java -> settings.allocationLimit?.java
            Language.kotlin -> settings.allocationLimit?.kotlin
        }?.takeIf { !settings.disableAllocationLimit }?.coerceAtLeast(MIN_ALLOCATION_LIMIT_BYTES)

        val plugins = listOf(
            ConfiguredSandboxPlugin(Jacoco, Unit),
            ConfiguredSandboxPlugin(
                ResourceMonitoring,
                ResourceMonitoringArguments(
                    submissionLineLimit = lineCountLimit,
                    allocatedMemoryLimit = allocationLimit,
                    individualAllocationLimit = MAX_INDIVIDUAL_ALLOCATION_BYTES
                )
            ),
            ConfiguredSandboxPlugin(VirtualFilesystem, VirtualFilesystemArguments())
        )

        val captureOutputControlInput = bindJeedCaptureOutputControlInput(systemInStream, settings.perTestOutputLimit)
        val taskResults = Sandbox.execute(
            compiledSubmission.classLoader,
            executionArguments,
            configuredPlugins = plugins
        ) { (classLoader, _) ->
            try {
                solution.submission(classLoader.loadClass(klassName)).test(jenisolSettings, captureOutputControlInput)
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            }
        }
        val failedClassInitializers = StaticFailureDetection.pollStaticInitializationFailures()
        if (failedClassInitializers.isNotEmpty()) {
            val missingPermissions = taskResults.permissionRequests
                .filter { !it.granted }
                .map { it.permission }
                .joinToString(", ")
            val message = "Failed classes: ${failedClassInitializers.joinToString(", ") { it.clazz.name }}.${
                if (missingPermissions.isNotEmpty()) {
                    " Missing permissions: $missingPermissions"
                } else {
                    ""
                }
            }"
            throw CachePoisonedException(message)
        }

        val threw = taskResults.returned?.threw ?: taskResults.threw
        val timeout = taskResults.timeout
        if (!timeout && threw is ThreadDeath) {
            throw CachePoisonedException("ThreadDeath")
        }

        results.taskResults = taskResults
        results.timeout = timeout
        val resourceUsage = taskResults.pluginResult(ResourceMonitoring)
        results.resourceMonitoringResults = resourceUsage

        val submissionExecutionCount = resourceUsage.submissionLines
        val solutionExecutionCount = if (language == Language.java) {
            validationResults?.executionCounts?.java ?: settings.solutionExecutionCount?.java
        } else {
            validationResults?.executionCounts?.kotlin ?: settings.solutionExecutionCount?.kotlin
        } ?: submissionExecutionCount
        results.lineCountTimeout = lineCountLimit != null && submissionExecutionCount > lineCountLimit

        // checkExecutedSubmission
        if (!timeout && threw != null) {
            results.failedSteps.add(TestResults.Step.checkExecutedSubmission)
            when (threw) {
                is ClassNotFoundException -> results.failed.checkExecutedSubmission =
                    "Class design error:\n  Could not find class ${published.klass}"

                is SubmissionDesignError -> results.failed.checkExecutedSubmission =
                    "Class design error:\n  ${threw.message}"

                is NoClassDefFoundError -> results.failed.checkExecutedSubmission =
                    "Class design error:\n  Attempted to use unavailable class ${threw.message}"

                is OutOfMemoryError -> results.failed.checkExecutedSubmission =
                    "Allocated too much memory: ${threw.message}, already used ${resourceUsage.allocatedMemory} bytes.\nIf you are printing for debug purposes, consider less verbose output."

                is LineLimitExceeded -> {
                    check(lineCountLimit != null) { "lineCountLimit should not be null" }
                    results.failed.checkExecutedSubmission =
                        "Executed too many lines: Already executed $lineCountLimit ${"line".pluralize(lineCountLimit.toInt())}, " +
                            "solution needed only $solutionExecutionCount total"
                }

                else -> {
                    val actualException = when (threw) {
                        is InvocationTargetException -> threw.targetException ?: threw
                        else -> threw
                    }
                    results.failed.checkExecutedSubmission =
                        "Testing generated an unexpected error: $actualException\n${actualException.stackTraceToString()}"
                }
            }
            return results
        }

        // HACK: lineCountLimit doesn't always seem to work properly
        if (lineCountLimit != null && submissionExecutionCount > lineCountLimit) {
            results.failedSteps.add(TestResults.Step.checkExecutedSubmission)
            results.failed.checkExecutedSubmission =
                "Executed too many lines: Already executed $lineCountLimit ${"line".pluralize(lineCountLimit.toInt())}, " +
                    "solution needed only $solutionExecutionCount total"
            return results
        }

        if (!checkExecutedSubmission(taskResults, results, language)) {
            return results
        }
        results.completedSteps.add(TestResults.Step.checkExecutedSubmission)

        results.complete.partial!!.passedSteps.design = true

        // testing
        if (taskResults.returned == null) {
            results.failedSteps.add(TestResults.Step.testing)
            return results
        }

        val testingResults = taskResults.returned!!.map { it.asTestResult(compiledSubmission.source) }
        val taskTestingResults = TestResults.TestingResult(
            testingResults,
            taskResults.returned!!.settings.testCount,
            taskResults.completed && !timeout,
            !taskResults.returned!!.finishedReceivers
        )
        results.addTestingResults(taskTestingResults)

        // tests passed partial credit
        val passedTestCount = testingResults.filter {
            !(fauxStatic && it.type == TestResult.Type.CONSTRUCTOR)
        }.count { it.passed }

        results.complete.partial!!.passedSteps.partiallyCorrect = passedTestCount > 0
        results.complete.partial!!.passedSteps.fullyCorrect = taskTestingResults.passed

        results.complete.partial!!.passedTestCount = TestResults.PartialCredit.PassedTestCount(
            passedTestCount,
            taskTestingResults.testCount,
            taskTestingResults.completed
        )

        testTestingIncorrect?.also {
            results.complete.partial!!.passedMutantCount = TestResults.PartialCredit.PassedMutantCount(
                testTestingIncorrect!!.count { it.testCount < passedTestCount },
                testTestingIncorrect!!.size,
                taskTestingResults.completed
            )
        }


        results.complete.recursion = checkRecursion(klassName, language, settings, isSolution, results)
        results.completedSteps.add(TestResults.Step.recursion)

        /*
    fun List<TestResults.TestingResult.TestResult>.recursiveMethods() = asSequence().filter {
        it.submissionResourceUsage!!.invokedRecursiveFunctions.isNotEmpty()
    }.map {
        it.jenisol!!.solutionExecutable
    }.filterIsInstance<Method>().map {
        ResourceMonitoringResults.MethodInfo(klassName, it.name, Type.getMethodDescriptor(it))
    }.toSet()

    val expectedRecursiveMethods = if (isSolution) {
        testingResults.recursiveMethods()
    } else {
        if (language == Language.java) {
            validationResults?.solutionRecursiveMethods?.java
                ?: settings.solutionRecursiveMethods?.java
        } else {
            validationResults?.solutionRecursiveMethods?.kotlin
                ?: settings.solutionRecursiveMethods?.kotlin
        }
    }

    check(expectedRecursiveMethods != null)

    if (isSolution) {
        results.foundRecursiveMethods = expectedRecursiveMethods
    }

    val missingRecursiveMethods = expectedRecursiveMethods - testingResults.recursiveMethods()
    if (missingRecursiveMethods.isNotEmpty() && passedTestCount > 0) {
        results.failed.checkExecutedSubmission =
            "Method ${missingRecursiveMethods.first().methodName} was not implemented recursively"
        results.failedSteps.add(TestResults.Step.checkExecutedSubmission)
        return results
    }
    */


        // executioncount soft failure
        results.complete.executionCount = TestResults.ResourceUsageComparison(
            solutionExecutionCount,
            submissionExecutionCount,
            (solutionExecutionCount * control.executionFailureMultiplier!!).toLong()
        )
        results.completedSteps.add(TestResults.Step.executioncount)

        // memoryAllocation
        val submissionAllocation = resourceUsage.allocatedMemory.coerceAtLeast(0)
        val solutionAllocation = if (language == Language.java) {
            validationResults?.memoryAllocation?.java ?: settings.solutionAllocation?.java
        } else {
            validationResults?.memoryAllocation?.kotlin ?: settings.solutionAllocation?.kotlin
        } ?: submissionAllocation

        results.complete.memoryAllocation = TestResults.ResourceUsageComparison(
            solutionAllocation,
            submissionAllocation,
            ((solutionAllocation.toDouble() * control.allocationFailureMultiplier!!)).toLong()
                .coerceAtLeast(MIN_ALLOCATION_FAILURE_BYTES)
        )
        results.completedSteps.add(TestResults.Step.memoryAllocation)

        // coverage
        check(settings.solutionDeadCode != null) { "Must set solutionDeadCode" }
        val solutionDeadCode = if (language == Language.java) {
            settings.solutionDeadCode!!.java
        } else {
            settings.solutionDeadCode!!.kotlin
        }!!

        val filetype = when (language) {
            Language.kotlin -> Source.FileType.KOTLIN
            Language.java -> Source.FileType.JAVA
        }
        val coverageResult = source
            .processCoverage(taskResults.pluginResult(Jacoco))
            .adjustWithFeatures(submissionFeatureResults, filetype)
            .byFile[filename(language)]!!
        val covered = coverageResult.count { it.value == LineCoverage.COVERED || it.value == LineCoverage.IGNORED }
        val total = coverageResult.count { it.value != LineCoverage.EMPTY }
        check(total - covered >= 0)

        val missed = coverageResult
            .filter { it.value == LineCoverage.NOT_COVERED || it.value == LineCoverage.PARTLY_COVERED }
            .map { it.key }

        val submissionCoverage = TestResults.CoverageComparison.LineCoverage(covered, total)
        val solutionCoverage =
            validationResults?.solutionCoverage ?: settings.solutionCoverage ?: submissionCoverage

        results.complete.coverage =
            TestResults.CoverageComparison(solutionCoverage, submissionCoverage, missed, solutionDeadCode.toInt())
        results.completedSteps.add(TestResults.Step.coverage)

        results.complete.partial!!.passedSteps.quality =
            results.complete.partial!!.passedSteps.fullyCorrect && results.complete.let {
                it.checkstyle?.errors?.isNotEmpty() == true ||
                    it.ktlint?.errors?.isNotEmpty() == true ||
                    it.complexity?.failed == true ||
                    it.features?.failed == true ||
                    it.lineCount?.failed == true ||
                    it.executionCount?.failed == true ||
                    it.memoryAllocation?.failed == true ||
                    it.coverage?.failed == true ||
                    it.classSize?.failed == true ||
                    it.recursion?.failed == true
            } == false

        return results
    } finally {
        testingCount++
    }
}