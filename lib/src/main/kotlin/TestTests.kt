package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.CheckstyleArguments
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CompilationArguments
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.CompiledSource
import edu.illinois.cs.cs125.jeed.core.ConfiguredSandboxPlugin
import edu.illinois.cs.cs125.jeed.core.FeatureName
import edu.illinois.cs.cs125.jeed.core.JeedClassLoader
import edu.illinois.cs.cs125.jeed.core.KompilationArguments
import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.LineLimitExceeded
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.checkstyle
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.features
import edu.illinois.cs.cs125.jeed.core.fromJavaSnippet
import edu.illinois.cs.cs125.jeed.core.fromKotlinSnippet
import edu.illinois.cs.cs125.jeed.core.fromTemplates
import edu.illinois.cs.cs125.jeed.core.kompile
import edu.illinois.cs.cs125.jeed.core.ktLint
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult
import edu.illinois.cs.cs125.jenisol.core.isPackagePrivate
import edu.illinois.cs.cs125.jenisol.core.isPrivate
import edu.illinois.cs.cs125.jenisol.core.isStatic
import edu.illinois.cs.cs125.questioner.lib.features.countFeature
import edu.illinois.cs.cs125.questioner.lib.features.usesLoop
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.lang.reflect.InvocationTargetException
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.random.Random

suspend fun Question.testTests(
    contents: String,
    language: Language,
    passedSettings: Question.TestTestingSettings? = null,
    limits: Question.TestTestingLimits = testTestingLimits!!
): TestTestResults {
    try {
        testingLimiter.acquire()

        val settings = Question.TestTestingSettings.DEFAULTS merge passedSettings

        check(published.type != Question.Type.SNIPPET) { "Test testing not supported for snippets" }
        check(settings.limit!! >= 2) { "Limit must be at least 2" }

        warm()

        val testKlass = "Test${published.klass}"
        val results = TestTestResults(language)

        // checkInitialTestTestingSubmission
        if (!(checkInitialTestTestingSubmission(contents, language, results))) {
            return results
        }

        val compilationClassLoader = when (language) {
            Language.java -> InvertingClassLoader(setOf(testKlass), compiledSolutionForTesting.classloader)
            Language.kotlin -> InvertingClassLoader(
                setOf(testKlass, "${testKlass}Kt"),
                compiledSolutionForTesting.classloader
            )
        }
        val compiledSubmission = try {
            when (language) {
                Language.java -> compileTestSuites(contents, compilationClassLoader, results)
                Language.kotlin -> kompileTestSuites(contents, compilationClassLoader, results)
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

        // checkCompiledTestSuite
        val klassName = checkCompiledTestSuite(compiledSubmission, results, language) ?: return results
        val testingIncorrect = testTestingIncorrect
        check(!testingIncorrect.isNullOrEmpty()) {
            "Value should not be null or empty"
        }

        val incorrectLimit = (settings.limit - 1).coerceAtMost(testingIncorrect.size)
        val testingMutations = when (settings.selectionStrategy!!) {
            Question.TestTestingSettings.SelectionStrategy.EASIEST -> testingIncorrect.take(incorrectLimit)
            Question.TestTestingSettings.SelectionStrategy.HARDEST -> testingIncorrect.takeLast(incorrectLimit)
            Question.TestTestingSettings.SelectionStrategy.EVENLY_SPACED -> {
                linspace(testingIncorrect.size - 1, incorrectLimit).map { testingIncorrect[it] }
            }
        }

        val random = if (settings.seed != null) {
            Random(settings.seed)
        } else {
            Random
        }
        val solutionPosition = random.nextInt(testingMutations.size + 1)

        val baseTimeout = (questionerTestTestTimeoutMS.toDouble() * control.timeoutMultiplier!!).toLong()

        val executionArguments = Sandbox.ExecutionArguments(
            timeout = baseTimeout * questionerWallClockTimeoutMultiplier,
            cpuTimeoutNS = baseTimeout * 1000L * 1000L,
            maxOutputLines = limits.outputLimit,
            permissions = Question.SAFE_PERMISSIONS,
            returnTimeout = Question.DEFAULT_RETURN_TIMEOUT,
            pollIntervalMS = (baseTimeout / 2).coerceAtLeast(1)
        )

        val lineCountLimit = when (language) {
            Language.java -> limits.executionCountLimit.java
            Language.kotlin -> limits.executionCountLimit.kotlin!!
        }
        val allocationLimit = when (language) {
            Language.java -> limits.allocationLimit.java
            Language.kotlin -> limits.allocationLimit.kotlin!!
        }.coerceAtLeast(MIN_ALLOCATION_LIMIT_BYTES)

        val plugins = listOf(
            ConfiguredSandboxPlugin(
                ResourceMonitoring,
                ResourceMonitoringArguments(
                    submissionLineLimit = lineCountLimit,
                    allocatedMemoryLimit = allocationLimit,
                    individualAllocationLimit = MAX_INDIVIDUAL_ALLOCATION_BYTES
                )
            ),
        )

        val testingClass = compiledSubmission.classLoader.loadClass(klassName)
        val staticTestingMethod = testingClass.getTestingMethod()!!.isStatic()
        if (!staticTestingMethod) {
            check(testingClass.declaredConstructors.find { it.parameters.isEmpty() } != null) {
                "Non-static testing method needs an empty constructor"
            }
        }

        var correct = 0
        var incorrect = 0
        var identifiedSolution: Boolean? = null

        val testTestingStarted = Instant.now()
        val output = mutableListOf<String>()

        var mutationIndex = 0
        for (i in 0 until testingMutations.size + 1) {
            val testingLoader = if (i == solutionPosition) {
                compiledSolutionForTesting
            } else {
                testingMutations[mutationIndex].compiled(this)
            }
            val usedMutationIndex = if (i == solutionPosition) {
                -1
            } else {
                mutationIndex
            }
            if (i != solutionPosition) {
                mutationIndex++
            }

            val isSolution = testingLoader == compiledSolutionForTesting

            val testingSuiteLoader = CopyableClassLoader.copy(compiledSubmission.classLoader, testingLoader.classloader)
            val taskResults = Sandbox.execute(
                testingSuiteLoader,
                executionArguments,
                configuredPlugins = plugins
            ) { (classLoader, _) ->
                return@execute try {
                    classLoader.loadClass(klassName).getTestingMethod()!!.also { method ->
                        method.isAccessible = true
                        if (staticTestingMethod) {
                            method.invoke(null)
                        } else {
                            method.invoke(
                                classLoader.loadClass(klassName).declaredConstructors.find { it.parameters.isEmpty() }!!
                                    .newInstance()
                            )
                        }
                    }
                } catch (e: InvocationTargetException) {
                    throw e.cause ?: e
                }
            }

            val timeout = taskResults.timeout
            val threw = taskResults.threw

            @Suppress("DEPRECATION", "removal")
            if (!taskResults.timeout && threw is ThreadDeath) {
                throw CachePoisonedException("ThreadDeath")
            }
            results.timeout = timeout

            val resourceUsage = taskResults.pluginResult(ResourceMonitoring)
            val submissionExecutionCount = resourceUsage.submissionLines
            results.lineCountTimeout = submissionExecutionCount > lineCountLimit

            if (results.timeout) {
                return results
            }

            if (results.lineCountTimeout) {
                results.failedSteps.add(TestTestResults.Step.checkExecutedSubmission)
                results.failed.checkExecutedSubmission =
                    "Executed too many lines: Already executed $lineCountLimit ${"line".pluralize(lineCountLimit.toInt())}, " +
                        "greater than the limit of $lineCountLimit"
                return results
            }

            when (threw) {
                is ClassNotFoundException -> results.failed.checkExecutedSubmission =
                    "Class design error:\n  Could not find class ${published.klass}"

                is NoClassDefFoundError -> results.failed.checkExecutedSubmission =
                    "Class design error:\n  Attempted to use unavailable class ${threw.message}"

                is OutOfMemoryError -> results.failed.checkExecutedSubmission =
                    "Allocated too much memory: ${threw.message}, already used ${resourceUsage.allocatedMemory} bytes.\nIf you are printing for debug purposes, consider less verbose output."

                is LineLimitExceeded -> {
                    results.failed.checkExecutedSubmission =
                        "Executed too many lines: Already executed $lineCountLimit ${"line".pluralize(lineCountLimit.toInt())}, " +
                            "greater than the limit of $lineCountLimit"
                }
            }

            if (results.failed.checkExecutedSubmission != null) {
                results.failedSteps.add(TestTestResults.Step.checkExecutedSubmission)
                return results
            }

            val isCorrect = if (isSolution) {
                taskResults.threw == null
            } else {
                taskResults.threw != null
            }
            if (!isCorrect && System.getenv("DEBUG_TEST_CORRECTNESS") != null) {
                if (isSolution) {
                    logger.debug { "${published.path}/${published.author}/${published.version}: tests marked solution as incorrect: ${taskResults.threw}" }
                } else {
                    logger.debug {
                        """${published.path}/${published.author}/${published.version}: missed incorrect mutation
                            |---${testingMutations[usedMutationIndex].deltas.joinToString("\n")}
                            |---"""
                    }
                }
            }
            if (isSolution) {
                identifiedSolution = isCorrect
            }
            if (isCorrect) {
                correct++
            } else {
                incorrect++
            }
            output += taskResults.stdout.trim() + if (taskResults.truncatedLines > 0) {
                "\n(${taskResults.truncatedLines} lines truncated)\n"
            } else {
                "\n"
            }
            if (incorrect > 0 && settings.shortCircuit!!) {
                break
            }
        }

        if (identifiedSolution != null) {
            identifiedSolution = identifiedSolution == true && correct > 1
        }

        results.addTestTestingResults(
            TestTestResults.TestTestingResults(
                correct,
                incorrect,
                identifiedSolution,
                testingMutations.size + 1,
                Instant.now().toEpochMilli() - testTestingStarted.toEpochMilli(),
                output
            )
        )
        return results
    } finally {
        testingLimiter.release()
    }
}

fun Question.templateTestSuites(
    contents: String,
    language: Language
): Pair<Source, String?> {
    val template = when (published.type) {
        Question.Type.KLASS -> null
        Question.Type.METHOD -> {
            when (language) {
                Language.java -> {
                    val templateImports = published.javaTestingImports ?: published.templateImports
                    """${templateImports.joinToString("\n") { "import $it;" }}
                        |
                        |public class Test${published.klass} extends ${published.klass} {
                        |  {{{ contents }}}
                        |}""".trimMargin().trimStart()
                }

                Language.kotlin -> {
                    val templateImports = published.kotlinTestingImports ?: published.kotlinImports ?: published.templateImports
                    """${templateImports.joinToString("\n") { "import $it;" }}
                        |
                        |class Test${published.klass} : ${published.klass}() {
                        |  {{{ contents }}}
                        |}""".trimMargin().trimStart()
                }
            }
        }

        Question.Type.SNIPPET -> error("Testing not supported for snippets")
    }

    val fileName = "Test${published.klass}.${language.extension()}"
    return if (template == null) {
        Pair(Source(mapOf(fileName to contents)), null)
    } else {
        Pair(
            Source.fromTemplates(
                mapOf(fileName to contents.trimEnd()),
                mapOf("$fileName.hbs" to template)
            ), template
        )
    }
}

@Suppress("ThrowsCount")
suspend fun Question.compileTestSuites(
    contents: String,
    parentClassLoader: ClassLoader,
    testResults: TestTestResults
): CompiledSource {
    return try {
        val (source, template) = templateTestSuites(contents, Language.java)
        if (template != null) {
            testResults.completedSteps.add(TestTestResults.Step.templateSubmission)
        }
        val compiledSource = source.compile(
            CompilationArguments(
                parentClassLoader = parentClassLoader,
                parentFileManager = compiledSolution.fileManager,
                parameters = true
            )
        ).also {
            testResults.complete.compileSubmission = CompiledSourceResult(it)
            testResults.completedSteps.add(TestTestResults.Step.compileSubmission)
        }
        testResults.addCheckstyleResults(source.checkstyle(CheckstyleArguments(failOnError = false)))
        compiledSource
    } catch (e: TemplatingFailed) {
        testResults.failed.templateSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.templateSubmission)
        throw e
    } catch (e: CheckstyleFailed) {
        testResults.failed.checkstyle = e
        testResults.failedSteps.add(TestTestResults.Step.checkstyle)
        throw e
    } catch (e: CompilationFailed) {
        testResults.failed.compileSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.compileSubmission)
        throw e
    }
}

@Suppress("ThrowsCount")
suspend fun Question.kompileTestSuites(
    contents: String,
    parentClassLoader: ClassLoader,
    testResults: TestTestResults
): CompiledSource {
    return try {
        val (source, template) = templateTestSuites(contents, Language.kotlin)
        if (template != null) {
            testResults.completedSteps.add(TestTestResults.Step.templateSubmission)
        }
        val compiledSource = source.kompile(
            KompilationArguments(
                parentClassLoader = parentClassLoader,
                parentFileManager = compiledSolution.fileManager,
                parameters = true
            )
        ).also {
            testResults.complete.compileSubmission = CompiledSourceResult(it)
            testResults.completedSteps.add(TestTestResults.Step.compileSubmission)
        }
        testResults.addKtlintResults(source.ktLint(KtLintArguments(failOnError = false)))
        compiledSource
    } catch (e: TemplatingFailed) {
        testResults.failed.templateSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.templateSubmission)
        throw e
    } catch (e: KtLintFailed) {
        testResults.failed.ktlint = e
        testResults.failedSteps.add(TestTestResults.Step.ktlint)
        throw e
    } catch (e: CompilationFailed) {
        testResults.failed.compileSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.compileSubmission)
        throw e
    }
}

private fun Class<*>.getTestingMethod() = declaredMethods.find { testingMethod ->
    testingMethod.name == "test" && testingMethod.parameters.isEmpty() && !testingMethod.isPrivate()
}

fun Question.checkCompiledTestSuite(
    compiledTestSuite: CompiledSource,
    testResults: TestTestResults,
    language: Language
): String? = compiledTestSuite.classLoader.definedClasses.topLevelClasses().let { klasses ->
    val testKlass = "Test${published.klass}"

    when {
        klasses.size != 1 -> {
            testResults.failed.checkCompiledSubmission =
                "Test suite should define a single public class with an empty or omitted constructor"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }
    }
    var klass = klasses.first()
    if (compiledTestSuite.source.type == Source.SourceType.KOTLIN &&
        (solution.skipReceiver || solution.fauxStatic) &&
        klass == "${testKlass}Kt"
    ) {
        klass = "${testKlass}Kt"
    } else {
        if (klass != testKlass) {
            testResults.failed.checkCompiledSubmission =
                "Test suite defines incorrect class: ${klasses.first()} != $testKlass"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }
    }
    compiledTestSuite.classLoader.loadClass(klass).also { testingKlass ->
        testingKlass.getTestingMethod() ?: run {
            testResults.failed.checkCompiledSubmission =
                "Test suite does not define a non-private static void testing method named test accepting no arguments"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }
        val fields = testingKlass.declaredFields.toSet().filter { field ->
            field.name != "${"$"}assertionsDisabled" && !(compiledTestSuite.source.type == Source.SourceType.KOTLIN && field.name == "Companion")
        }
        if (fields.isNotEmpty()) {
            testResults.failed.checkCompiledSubmission =
                "Testing class may not declare fields"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }
    }

    val features = compiledTestSuite.source.features().lookup("", testFilename(language)).features
    if (features.usesLoop()) {
        testResults.failed.checkCompiledSubmission = "Testing code may not use loops"
        testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
        return null
    }
    if (features.countFeature(FeatureName.METHOD) > 1) {
        testResults.failed.checkCompiledSubmission = "Testing code may not define extra methods"
        testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
        return null
    }

    return klass
}

class CopyableClassLoader(override val bytecodeForClasses: Map<String, ByteArray>, parent: ClassLoader) :
    ClassLoader(parent), Sandbox.SandboxableClassLoader {
    override val classLoader: ClassLoader = this

    override fun findClass(name: String): Class<*> {
        return if (name in bytecodeForClasses) {
            return defineClass(name, bytecodeForClasses[name]!!, 0, bytecodeForClasses[name]!!.size)
        } else {
            super.findClass(name)
        }
    }

    companion object {
        fun copy(classLoader: JeedClassLoader, parent: ClassLoader) =
            CopyableClassLoader(classLoader.bytecodeForClasses, parent)
    }
}

fun Question.fixTestingMethods(classLoader: JeedClassLoader): ClassLoader {
    val methodsToOpen = classLoader.loadClass(published.klass).declaredMethods
        .filter { method -> method.isPackagePrivate() }
        .map { method -> method.name }
    val classReader = ClassReader(classLoader.bytecodeForClasses[published.klass])
    val classWriter = ClassWriter(classReader, 0)
    val openingVisitor = object : ClassVisitor(Opcodes.ASM8, classWriter) {
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ) = when (name) {
            in methodsToOpen -> super.visitMethod(
                access or Opcodes.ACC_PUBLIC,
                name,
                descriptor,
                signature,
                exceptions
            )

            else -> super.visitMethod(access, name, descriptor, signature, exceptions)
        }
    }
    classReader.accept(openingVisitor, 0)
    return CopyableClassLoader(mapOf(published.klass to classWriter.toByteArray()), classLoader.parent)
}

@Suppress("SpellCheckingInspection")
fun linspace(stop: Int, num: Int): List<Int> {
    check(num <= stop + 1) { "Bad num value" }
    if (num == 1) {
        return listOf(stop)
    }
    val step = stop.toDouble() / (num - 1)
    return (0 until num).map { (it * step).roundToInt() }.distinct().also {
        check(it.contains(stop)) { "$stop $num: $it does not contain $stop" }
        check(it.size == num) { "$stop $num: $it does not have size $num" }
    }
}

fun Question.checkInitialTestTestingSubmission(
    contents: String,
    language: Language,
    testResults: TestTestResults
): Boolean {
    val snippetProperties = try {
        when (language) {
            Language.java -> Source.fromJavaSnippet(contents)
            Language.kotlin -> Source.fromKotlinSnippet(contents)
        }.snippetProperties
    } catch (e: Exception) {
        testResults.completedSteps.add(TestTestResults.Step.checkInitialSubmission)
        // If the code doesn't parse as a snippet, fall back to compiler error messages which are usually more useful
        return true
    }
    when (published.type) {
        Question.Type.SNIPPET -> error("Snippets not supported for test testing")

        Question.Type.METHOD -> {
            if (snippetProperties.importCount > 0) {
                testResults.failed.checkInitialSubmission = "import statements are not allowed for this problem"
            } else if (snippetProperties.classCount > 0) {
                testResults.failed.checkInitialSubmission = "Class declarations are not allowed for this problem"
            } else if (snippetProperties.looseCount > 0) {
                testResults.failed.checkInitialSubmission =
                    "Submission should be a single testing method with no code outside"
            }
        }

        Question.Type.KLASS -> {
            if (language == Language.java) {
                if (snippetProperties.methodCount > 0) {
                    testResults.failed.checkInitialSubmission =
                        "Top-level method declarations are not allowed for this problem"
                }
            } else if (language == Language.kotlin) {
                if (snippetProperties.classCount > 0 && snippetProperties.methodCount > 0) {
                    testResults.failed.checkInitialSubmission =
                        "Can't mix top-level classes and methods for this problem"
                }
            }
            if (snippetProperties.looseCount > 0) {
                testResults.failed.checkInitialSubmission =
                    "Submission should be a single testing class with no code outside"
            } else if (snippetProperties.classCount > 1) {
                testResults.failed.checkInitialSubmission = "Submission should define a single class"
            }
        }
    }
    return if (testResults.failed.checkInitialSubmission != null) {
        testResults.failedSteps.add(TestTestResults.Step.checkInitialSubmission)
        false
    } else {
        testResults.completedSteps.add(TestTestResults.Step.checkInitialSubmission)
        true
    }
}
