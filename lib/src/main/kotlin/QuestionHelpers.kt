package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.CheckstyleArguments
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CompilationArguments
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.CompiledSource
import edu.illinois.cs.cs125.jeed.core.Features
import edu.illinois.cs.cs125.jeed.core.KompilationArguments
import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.allFixedMutations
import edu.illinois.cs.cs125.jeed.core.checkstyle
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.complexity
import edu.illinois.cs.cs125.jeed.core.countLines
import edu.illinois.cs.cs125.jeed.core.fromJavaSnippet
import edu.illinois.cs.cs125.jeed.core.fromKotlinSnippet
import edu.illinois.cs.cs125.jeed.core.fromTemplates
import edu.illinois.cs.cs125.jeed.core.kompile
import edu.illinois.cs.cs125.jeed.core.ktLint
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult
import edu.illinois.cs.cs125.jeed.core.stripComments
import edu.illinois.cs.cs125.jenisol.core.CaptureOutputControlInput
import edu.illinois.cs.cs125.jenisol.core.CapturedResult
import edu.illinois.cs.cs125.jenisol.core.unwrap
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.random.Random

internal fun String.pluralize(count: Int, plural: String? = null) = if (count == 1) {
    this
} else plural ?: "${this}s"

fun Question.templateSubmission(contents: String, language: Question.Language): Source {
    val template = getTemplate(language)
    return if (template == null) {
        Source(mapOf(filename(language) to contents))
    } else {
        Source.fromTemplates(
            mapOf(filename(language) to contents.trimEnd()),
            mapOf("${filename(language)}.hbs" to template)
        )
    }
}

fun Question.contentsToSource(contents: String, language: Question.Language, testResults: TestResults): Source {
    val cleanedContents = contents.lines().joinToString("\n") {
        it.ifBlank { "" }
    }
    return templateSubmission(cleanedContents, language).also {
        if (getTemplate(language) != null) {
            testResults.completedSteps.add(TestResults.Step.templateSubmission)
        }
    }
}

fun Question.checkInitialSubmission(contents: String, language: Question.Language, testResults: TestResults): Boolean {
    val snippetProperties = try {
        when (language) {
            Question.Language.java -> Source.fromJavaSnippet(contents)
            Question.Language.kotlin -> Source.fromKotlinSnippet(contents)
        }.snippetProperties
    } catch (e: Exception) {
        testResults.completedSteps.add(TestResults.Step.checkInitialSubmission)
        // If the code doesn't parse as a snippet, fall back to compiler error messages which are usually more useful
        return true
    }
    when (type) {
        Question.Type.SNIPPET -> {
            if (snippetProperties.importCount > 0) {
                testResults.failed.checkInitialSubmission = "import statements are not allowed for this problem"
            } else if (snippetProperties.methodCount > 0) {
                testResults.failed.checkInitialSubmission = "Method declarations are not allowed for this problem"
            } else if (snippetProperties.classCount > 0) {
                testResults.failed.checkInitialSubmission = "Class declarations are not allowed for this problem"
            }
        }

        Question.Type.METHOD -> {
            if (snippetProperties.importCount > 0) {
                testResults.failed.checkInitialSubmission = "import statements are not allowed for this problem"
            } else if (snippetProperties.classCount > 0) {
                testResults.failed.checkInitialSubmission = "Class declarations are not allowed for this problem"
            } else if (snippetProperties.looseCount > 0) {
                testResults.failed.checkInitialSubmission =
                    "Submission should be a single method declaration with no code outside"
            }
        }

        Question.Type.KLASS -> {
            if (language == Question.Language.java) {
                if (snippetProperties.methodCount > 0) {
                    testResults.failed.checkInitialSubmission =
                        "Top-level method declarations are not allowed for this problem"
                }
            } else if (language == Question.Language.kotlin) {
                if (snippetProperties.classCount > 0 && snippetProperties.methodCount > 0) {
                    testResults.failed.checkInitialSubmission =
                        "Can't mix top-level classes and methods for this problem"
                }
            }
            if (snippetProperties.looseCount > 0) {
                testResults.failed.checkInitialSubmission = "Submission should be a single class with no code outside"
            } else if (snippetProperties.classCount > 1) {
                testResults.failed.checkInitialSubmission = "Submission should define a single class"
            }
        }
    }
    return if (testResults.failed.checkInitialSubmission != null) {
        testResults.failedSteps.add(TestResults.Step.checkInitialSubmission)
        false
    } else {
        testResults.completedSteps.add(TestResults.Step.checkInitialSubmission)
        true
    }
}

@Suppress("ThrowsCount")
suspend fun Question.compileSubmission(
    source: Source,
    parentClassLoader: ClassLoader,
    testResults: TestResults
): CompiledSource {
    return try {
        val actualParents = Pair(compiledCommon?.classLoader ?: parentClassLoader, compiledCommon?.fileManager)
        val compiledSource = source.compile(
            CompilationArguments(
                parentClassLoader = actualParents.first,
                parentFileManager = actualParents.second,
                parameters = true
            )
        ).also {
            testResults.complete.compileSubmission = CompiledSourceResult(it)
            testResults.completedSteps.add(TestResults.Step.compileSubmission)
        }
        testResults.addCheckstyleResults(
            source.checkstyle(
                CheckstyleArguments(
                    failOnError = false,
                    suppressions = checkstyleSuppressions
                )
            )
        )
        compiledSource
    } catch (e: TemplatingFailed) {
        testResults.failed.templateSubmission = e
        testResults.failedSteps.add(TestResults.Step.templateSubmission)
        throw e
    } catch (e: CheckstyleFailed) {
        testResults.failed.checkstyle = e
        testResults.failedSteps.add(TestResults.Step.checkstyle)
        throw e
    } catch (e: CompilationFailed) {
        testResults.failed.compileSubmission = e
        testResults.failedSteps.add(TestResults.Step.compileSubmission)
        throw e
    }
}

@Suppress("ThrowsCount")
suspend fun Question.kompileSubmission(
    source: Source,
    parentClassLoader: ClassLoader,
    testResults: TestResults
): CompiledSource {
    return try {
        val actualParents = Pair(compiledCommon?.classLoader ?: parentClassLoader, compiledCommon?.fileManager)
        val compiledSource = source.kompile(
            KompilationArguments(
                parentClassLoader = actualParents.first,
                parentFileManager = actualParents.second,
                parameters = true
            )
        ).also {
            testResults.complete.compileSubmission = CompiledSourceResult(it)
            testResults.completedSteps.add(TestResults.Step.compileSubmission)
        }
        testResults.addKtlintResults(
            source.ktLint(
                KtLintArguments(
                    failOnError = false,
                    indent = 2,
                    maxLineLength = 120
                )
            )
        )
        compiledSource
    } catch (e: TemplatingFailed) {
        testResults.failed.templateSubmission = e
        testResults.failedSteps.add(TestResults.Step.templateSubmission)
        throw e
    } catch (e: KtLintFailed) {
        testResults.failed.ktlint = e
        testResults.failedSteps.add(TestResults.Step.ktlint)
        throw e
    } catch (e: CompilationFailed) {
        testResults.failed.compileSubmission = e
        testResults.failedSteps.add(TestResults.Step.compileSubmission)
        throw e
    }
}

fun Question.checkCompiledSubmission(
    compiledSubmission: CompiledSource,
    testResults: TestResults
): String? = compiledSubmission.classLoader.definedClasses.topLevelClasses().let {
    when {
        it.isEmpty() -> {
            testResults.failed.checkCompiledSubmission = "Submission defined no classes"
            testResults.failedSteps.add(TestResults.Step.checkCompiledSubmission)
            return null
        }

        it.size > 1 -> {
            testResults.failed.checkCompiledSubmission = "Submission defined multiple classes"
            testResults.failedSteps.add(TestResults.Step.checkCompiledSubmission)
            return null
        }
    }
    var klass = it.first()
    if (compiledSubmission.source.type == Source.FileType.KOTLIN &&
        (solution.skipReceiver || solution.fauxStatic) &&
        klass == "${compilationDefinedClass}Kt"
    ) {
        klass = "${compilationDefinedClass}Kt"
    } else {
        if (klass != compilationDefinedClass) {
            testResults.failed.checkCompiledSubmission =
                "Submission defines incorrect class: ${it.first()} != $compilationDefinedClass"
            testResults.failedSteps.add(TestResults.Step.checkCompiledSubmission)
            return null
        }
    }
    return klass
}

@Suppress("ReturnCount")
fun Question.checkExecutedSubmission(
    taskResults: Sandbox.TaskResults<*>,
    testResults: TestResults,
    language: Question.Language
): Boolean {
    var message: String? = null
    taskResults.sandboxedClassLoader!!.definedClasses.topLevelClasses().let {
        when {
            it.isEmpty() -> message = "Submission defined no classes"
            it.size > 1 -> message = "Submission defined multiple classes"
        }
        val klass = it.first()
        if (!(
                language == Question.Language.kotlin &&
                    solution.skipReceiver &&
                    klass == "${compilationDefinedClass}Kt"
                )
        ) {
            if (klass != compilationDefinedClass) {
                message =
                    "Submission defines incorrect class: ${it.first()} != $compilationDefinedClass"
            }
        }
    }
    taskResults.sandboxedClassLoader!!.loadedClasses.find { imported ->
        importBlacklist.any { imported.startsWith(it) }
    }?.let {
        message = "Cannot use $it for this problem"
    }
    taskResults.permissionRequests.filter { !it.granted }.let { denied ->
        if (denied.isNotEmpty()) {
            val deniedPermission = denied.find { it.permission.name.startsWith("loadClass") }
            message = if (deniedPermission != null) {
                "Cannot use ${deniedPermission.permission.name.removePrefix("loadClass ")} for this problem"
            } else {
                "Submission permission requests were denied: ${denied.first().permission}"
            }
        }
    }
    return if (message != null) {
        testResults.failed.checkExecutedSubmission = message
        testResults.failedSteps.add(TestResults.Step.checkExecutedSubmission)
        false
    } else {
        true
    }
}

suspend fun Question.mutations(seed: Int, count: Int) =
    templateSubmission(
        if (getTemplate(Question.Language.java) != null) {
            "// TEMPLATE_START\n" + correct.contents + "\n// TEMPLATE_END \n"
        } else {
            correct.contents
        }, Question.Language.java
    ).allFixedMutations(random = Random(seed)).mapNotNull {
        try {
            it.formatted()
        } catch (e: Exception) {
            println(
                """
Failed to format mutation sources for mutation type: ${it.mutations.firstOrNull()!!.mutation.mutationType}

Mutated:
---
${it.contents}
---

Original:
---
${it.originalSources.contents}
---

Please report a bug so that we can improve the mutation engine.
            """.trimIndent()
            )
            null
        }
    }
        .map {
            // Mutations will sometimes break the entire template
            Pair(
                try {
                    it.contents.deTemplate(getTemplate(Question.Language.java))
                } catch (e: Exception) {
                    correct.contents
                }, it
            )
        }
        .filter { (contents, _) ->
            // Templated questions sometimes will mutate the template
            contents != correct.contents
        }
        .distinctBy { (contents, _) ->
            contents.stripComments(Source.FileType.JAVA).hashCode()
        }
        .shuffled(random = Random(seed))
        .take(count)
        .map { (contents, source) ->
            Question.IncorrectFile(
                klass,
                contents,
                Question.IncorrectFile.Reason.TEST,
                Question.Language.java,
                null,
                false,
                mutation = source
            )
        }

class MaxComplexityExceeded(message: String) : RuntimeException(message)

fun Question.computeComplexity(contents: String, language: Question.Language): TestResults.ComplexityComparison {
    val solutionComplexity = published.complexity[language]
    check(solutionComplexity != null) { "Solution complexity not available" }

    val maxComplexity =
        (control.maxComplexityMultiplier!! * solutionComplexity).coerceAtLeast(Question.TestingControl.DEFAULT_MIN_FAIL_FAST_COMPLEXITY)

    val submissionComplexity = when {
        type == Question.Type.SNIPPET && contents.isBlank() -> 0
        language == Question.Language.java -> {
            val source = when (type) {
                Question.Type.KLASS -> Source(mapOf("$klass.java" to contents))
                Question.Type.METHOD -> Source(
                    mapOf(
                        "$klass.java" to """
public class $klass {
$contents
}""".trimStart()
                    )
                )

                Question.Type.SNIPPET -> Source.fromJavaSnippet(contents, trim = false)
            }
            source.complexity().let { results ->
                when (type) {
                    Question.Type.KLASS -> results.lookupFile("$klass.java")
                    Question.Type.METHOD -> results.lookup(klass, "$klass.java").complexity
                    Question.Type.SNIPPET -> results.lookup("").complexity
                }
            }
        }

        language == Question.Language.kotlin -> {
            val source = when (type) {
                Question.Type.SNIPPET -> Source.fromKotlinSnippet(contents, trim = false)
                else -> Source(mapOf("$klass.kt" to contents))
            }
            source.complexity().let { results ->
                when (type) {
                    Question.Type.SNIPPET -> results.lookup("").complexity
                    else -> results.lookupFile("$klass.kt")
                }
            }
        }

        else -> error("Shouldn't get here")
    }
    if (submissionComplexity > maxComplexity) {
        throw MaxComplexityExceeded(
            "Submission complexity $submissionComplexity exceeds maximum of $maxComplexity.\n" +
                "The solution has $solutionComplexity code ${"path".pluralize(solutionComplexity)}."
        )
    }
    return TestResults.ComplexityComparison(solutionComplexity, submissionComplexity, control.maxExtraComplexity!!)
}

fun Question.checkFeatures(
    submissionFeatures: Features,
    language: Question.Language
): TestResults.FeaturesComparison {
    val solutionFeatures = published.features[language]
    check(solutionFeatures != null) { "Solution features not available" }

    val errors = if (featureChecker != null) {
        @Suppress("UNCHECKED_CAST")
        unwrap { featureChecker!!.invoke(null, solutionFeatures, submissionFeatures) } as List<String>
    } else {
        listOf()
    }

    return TestResults.FeaturesComparison(errors)
}

class MaxLineCountExceeded(message: String) : RuntimeException(message)

fun Question.computeLineCounts(contents: String, language: Question.Language): TestResults.LineCountComparison {
    val solutionLineCount = published.lineCounts[language]
    check(solutionLineCount != null) { "Solution line count not available" }

    val maxLineCount = (control.maxLineCountMultiplier!! * solutionLineCount.source)

    val type = when (language) {
        Question.Language.java -> Source.FileType.JAVA
        Question.Language.kotlin -> Source.FileType.KOTLIN
    }
    val submissionLineCount = contents.countLines(type)
    if (submissionLineCount.source > maxLineCount) {
        throw MaxLineCountExceeded(
            "Submission line count ${submissionLineCount.source} exceeds maximum of $maxLineCount.\n" +
                "The solution has ${solutionLineCount.source} source ${"line".pluralize(solutionLineCount.source)}."
        )
    }
    return TestResults.LineCountComparison(
        solutionLineCount,
        submissionLineCount,
        (solutionLineCount.source * control.sourceLinesMultiplier!!).toInt(),
        control.minExtraSourceLines!!
    )
}

class InvertingClassLoader(
    private val inversions: Set<String>,
    parent: ClassLoader = getSystemClassLoader()
) : ClassLoader(parent) {
    // Invert the usual delegation strategy for classes in this package to avoid using the system ClassLoader
    override fun loadClass(name: String): Class<*> {
        return if (name in inversions) {
            throw ClassNotFoundException()
        } else {
            super.loadClass(name)
        }
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return if (name in inversions) {
            throw ClassNotFoundException()
        } else {
            super.loadClass(name, resolve)
        }
    }
}

class BumpingInputStream : InputStream() {
    private var stream = ByteArrayInputStream("".toByteArray())
    private lateinit var inputs: List<ByteArray>

    private var index = 0
    private var usedIndex = false

    fun setInputs(ourInputs: List<ByteArray>) {
        index = 0
        usedIndex = false
        inputs = ourInputs
        stream = ByteArrayInputStream(inputs.getOrNull(index) ?: "".toByteArray())
    }

    fun bump() {
        if (usedIndex) {
            index++
            stream = ByteArrayInputStream(inputs.getOrNull(index) ?: "".toByteArray())
            usedIndex = false
        }
    }

    override fun read(): Int {
        usedIndex = true
        return stream.read()
    }
}

fun bindJeedCaptureOutputControlInput(
    stdinStream: BumpingInputStream,
    perTestOutputLimit: Int
): CaptureOutputControlInput {
    return fun(stdin: List<String>, run: () -> Any?): CapturedResult {
        stdinStream.setInputs(stdin.map { "$it\n".toByteArray() })

        val outputListener = object : Sandbox.OutputListener {
            override fun stdout(int: Int) {
                stdinStream.bump()
            }

            override fun stderr(int: Int) {}
        }
        var resourceUsage: ResourceMonitoringCheckpoint? = null
        val jeedOutput = Sandbox.redirectOutput(outputListener, perTestOutputLimit) {
            ResourceMonitoring.beginSubmissionCall()
            try {
                run()
            } finally {
                resourceUsage = ResourceMonitoring.finishSubmissionCall()
            }
        }
        return CapturedResult(
            jeedOutput.returned,
            jeedOutput.threw,
            jeedOutput.stdout,
            jeedOutput.stderr,
            jeedOutput.stdin,
            jeedOutput.interleavedInputOutput,
            jeedOutput.truncatedLines,
            resourceUsage
        )
    }
}