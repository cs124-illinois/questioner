package edu.illinois.cs.cs125.questioner.lib

import com.beyondgrader.resourceagent.jeed.IsolatedFileSystemProvider
import com.beyondgrader.resourceagent.jeed.VirtualFilesystem
import com.beyondgrader.resourceagent.jeed.populate
import edu.illinois.cs.cs125.jeed.core.CheckstyleArguments
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CompilationArguments
import edu.illinois.cs.cs125.jeed.core.CompilationError
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.CompiledSource
import edu.illinois.cs.cs125.jeed.core.Features
import edu.illinois.cs.cs125.jeed.core.JeedClassLoader
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
import edu.illinois.cs.cs125.jeed.core.getEmptyJavaClassSize
import edu.illinois.cs.cs125.jeed.core.getEmptyKotlinClassSize
import edu.illinois.cs.cs125.jeed.core.kompile
import edu.illinois.cs.cs125.jeed.core.ktLint
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult
import edu.illinois.cs.cs125.jeed.core.stripComments
import edu.illinois.cs.cs125.jenisol.core.CaptureOutputControlInput
import edu.illinois.cs.cs125.jenisol.core.CapturedResult
import edu.illinois.cs.cs125.jenisol.core.isPackagePrivate
import edu.illinois.cs.cs125.jenisol.core.isProtected
import edu.illinois.cs.cs125.jenisol.core.unwrap
import edu.illinois.cs.cs125.questioner.lib.compilation.filterSuppressions
import edu.illinois.cs.cs125.questioner.lib.compilation.removeExpected
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import kotlin.random.Random

internal fun String.pluralize(count: Int, plural: String? = null) = if (count == 1) {
    this
} else plural ?: "${this}s"

fun Question.templateSubmission(contents: String, language: Language): Source {
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

fun Question.contentsToSource(contents: String, language: Language, testResults: TestResults): Source {
    val cleanedContents = contents.lines().joinToString("\n") {
        it.ifBlank { "" }
    }
    return templateSubmission(cleanedContents, language).also {
        if (getTemplate(language) != null) {
            testResults.completedSteps.add(TestResults.Step.templateSubmission)
        }
    }
}

fun Question.checkInitialSubmission(contents: String, language: Language, testResults: TestResults): Boolean {
    val snippetProperties = try {
        when (language) {
            Language.java -> Source.fromJavaSnippet(contents)
            Language.kotlin -> Source.fromKotlinSnippet(contents)
        }.snippetProperties
    } catch (_: Exception) {
        testResults.completedSteps.add(TestResults.Step.checkInitialSubmission)
        // If the code doesn't parse as a snippet, fall back to compiler error messages which are usually more useful
        return true
    }
    when (published.type) {
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
    testResults: TestResults,
    suppressions: Set<String>
): CompiledSource {
    return try {
        val actualParents = Pair(compiledCommon?.classLoader ?: parentClassLoader, compiledCommon?.fileManager)
        val compiledSource = source.compile(
            CompilationArguments(
                parentClassLoader = actualParents.first,
                parentFileManager = actualParents.second,
                parameters = true,
            )
        ).also { compiledSource ->
            val errorMessages = compiledSource.messages.removeExpected().filterSuppressions(suppressions)
            if (errorMessages.isNotEmpty()) {
                throw CompilationFailed(errorMessages.map { CompilationError(it.location, it.message) })
            }
            testResults.complete.compileSubmission = CompiledSourceResult(compiledSource)
            testResults.completedSteps.add(TestResults.Step.compileSubmission)
        }
        val checkstyleSuppressions = suppressions
            .map {
                if (it == "checkstyle") {
                    "checkstyle:*"
                } else {
                    it
                }
            }
            .filter { it.startsWith("checkstyle:") }
            .map { it.removePrefix("checkstyle:") }
            .toSet()
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
    testResults: TestResults,
    @Suppress("UNUSED_PARAMETER") suppressions: Set<String>
): CompiledSource {
    return try {
        val actualParents = Pair(compiledCommon?.classLoader ?: parentClassLoader, compiledCommon?.fileManager)
        val compiledSource = source.kompile(
            KompilationArguments(
                parentClassLoader = actualParents.first,
                parentFileManager = actualParents.second,
                parameters = true,
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
                    maxLineLength = 120,
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
    val klass = it.first()
    if (compiledSubmission.source.type != Source.SourceType.KOTLIN || !(solution.skipReceiver || solution.fauxStatic) || klass != "${compilationDefinedClass}Kt") {
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
    language: Language
): Boolean {
    var message: String? = null
    taskResults.sandboxedClassLoader!!.definedClasses.topLevelClasses().let {
        when {
            it.isEmpty() -> message = "Submission defined no classes"
            it.size > 1 -> message = "Submission defined multiple classes"
        }
        val klass = it.first()
        if (!(
                language == Language.kotlin &&
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

suspend fun Question.javaMutations(seed: Int, count: Int) =
    templateSubmission(
        if (getTemplate(Language.java) != null) {
            "// TEMPLATE_START\n" + solutionByLanguage[Language.java]!!.contents + "\n// TEMPLATE_END \n"
        } else {
            solutionByLanguage[Language.java]!!.contents
        }, Language.java
    ).allFixedMutations(random = Random(seed)).mapNotNull {
        try {
            it.formatted()
        } catch (_: Exception) {
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
                    it.contents.deTemplate(getTemplate(Language.java))
                } catch (_: Exception) {
                    getCorrect(Language.java)!!
                }, it
            )
        }
        .filter { (contents, _) ->
            // Templated questions sometimes will mutate the template
            contents != getCorrect(Language.java)!!
        }
        .distinctBy { (contents, _) ->
            contents.stripComments(Source.FileType.JAVA).hashCode()
        }
        .shuffled(random = Random(seed))
        .take(count)
        .map { (contents, source) ->
            Question.IncorrectFile(
                published.klass,
                contents,
                Question.IncorrectFile.Reason.TEST,
                Language.java,
                null,
                false,
                mutation = source,
                mutationSourceLanguage = Language.java
            )
        }

suspend fun Question.kotlinMutations(seed: Int, count: Int): List<Question.IncorrectFile> {
    val kotlinSolution = solutionByLanguage[Language.kotlin] ?: return emptyList()
    
    return templateSubmission(
        if (getTemplate(Language.kotlin) != null) {
            "// TEMPLATE_START\n" + kotlinSolution.contents + "\n// TEMPLATE_END \n"
        } else {
            kotlinSolution.contents
        }, Language.kotlin
    ).allFixedMutations(random = Random(seed)).mapNotNull {
        try {
            it.formatted()
        } catch (_: Exception) {
            if (it.mutations.first().mutation.mightNotCompile) {
                return@mapNotNull null
            }
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
                    it.contents.deTemplate(getTemplate(Language.kotlin))
                } catch (_: Exception) {
                    getCorrect(Language.kotlin)!!
                }, it
            )
        }
        .filter { (contents, _) ->
            // Templated questions sometimes will mutate the template
            contents != getCorrect(Language.kotlin)!!
        }
        .distinctBy { (contents, _) ->
            contents.stripComments(Source.FileType.KOTLIN).hashCode()
        }
        .shuffled(random = Random(seed))
        .take(count)
        .map { (contents, source) ->
            Question.IncorrectFile(
                published.klass,
                contents,
                Question.IncorrectFile.Reason.TEST,
                Language.kotlin,
                null,
                false,
                mutation = source,
                mutationSourceLanguage = Language.kotlin
            )
        }
}


class MaxClassSizeExceeded(message: String) : RuntimeException(message)

private object EmptyClassSizes {
    val EMPTY_JAVA_CLASS_SIZE by lazy {
        getEmptyJavaClassSize()
    }
    val EMPTY_KOTLIN_CLASS_SIZE by lazy {
        getEmptyKotlinClassSize()
    }
}

fun Question.computeClassSize(
    compiledSubmission: CompiledSource,
    language: Language,
    settings: Question.TestingSettings
): TestResults.ResourceUsageComparison {
    val emptyClassSize = when (language) {
        Language.java -> EmptyClassSizes.EMPTY_JAVA_CLASS_SIZE
        Language.kotlin -> EmptyClassSizes.EMPTY_KOTLIN_CLASS_SIZE
    }
    val submissionClassSize = (compiledSubmission.classLoader.sizeInBytes.toLong() - emptyClassSize).let { size ->
        when {
            size < 0 -> {
                logger.debug { "Negative relative class size value: $size" }
                0
            }

            else -> size
        }
    }
    val solutionClassSize = when (language) {
        Language.java -> validationResults?.solutionMaxClassSize?.java ?: settings.solutionClassSize?.java
        Language.kotlin -> validationResults?.solutionMaxClassSize?.kotlin
            ?: settings.solutionClassSize?.kotlin
    } ?: submissionClassSize

    val maxClassSize = solutionClassSize * Question.TestingControl.DEFAULT_MIN_FAIL_FAST_CLASS_SIZE_MULTIPLIER

    if (submissionClassSize > maxClassSize) {
        throw MaxClassSizeExceeded(
            "Submission class size $submissionClassSize exceeds maximum of $maxClassSize.\n" +
                "The solution has class size of $solutionClassSize."
        )
    }
    return TestResults.ResourceUsageComparison(
        solutionClassSize,
        submissionClassSize,
        (solutionClassSize.toDouble() * control.maxClassSizeMultiplier!!).toLong()
    )
}

class MaxComplexityExceeded(message: String) : RuntimeException(message)

fun Question.computeComplexity(contents: String, language: Language): TestResults.ComplexityComparison {
    val solutionComplexity = classification.complexity[language]
    check(solutionComplexity != null) { "Solution complexity not available for $language" }

    val maxComplexity =
        (control.maxComplexityMultiplier!! * solutionComplexity)
            .toInt()
            .coerceAtLeast(Question.TestingControl.DEFAULT_MIN_FAIL_FAST_COMPLEXITY)

    val submissionComplexity = when {
        published.type == Question.Type.SNIPPET && contents.isBlank() -> 0
        language == Language.java -> {
            val source = when (published.type) {
                Question.Type.KLASS -> Source(mapOf("${published.klass}.java" to contents))
                Question.Type.METHOD -> Source(
                    mapOf(
                        "${published.klass}.java" to """
public class ${published.klass} {
$contents
}""".trimStart()
                    )
                )

                Question.Type.SNIPPET -> Source.fromJavaSnippet(contents, trim = false)
            }
            source.complexity().let { results ->
                when (published.type) {
                    Question.Type.KLASS -> results.lookupFile("${published.klass}.java")
                    Question.Type.METHOD -> results.lookup(published.klass, "${published.klass}.java").complexity
                    Question.Type.SNIPPET -> results.lookup("").complexity
                }
            }
        }

        language == Language.kotlin -> {
            val source = when (published.type) {
                Question.Type.SNIPPET -> Source.fromKotlinSnippet(contents, trim = false)
                else -> Source(mapOf("${published.klass}.kt" to contents))
            }
            source.complexity().let { results ->
                when (published.type) {
                    Question.Type.SNIPPET -> results.lookup("").complexity
                    else -> results.lookupFile("${published.klass}.kt")
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
    language: Language
): TestResults.FeaturesComparison {
    val solutionFeatures = classification.featuresByLanguage[language]
    check(solutionFeatures != null) { "Solution features not available" }

    val errors = if (featureChecker != null) {
        @Suppress("UNCHECKED_CAST")
        if (featureChecker!!.parameters.size == 2) {
            unwrap { featureChecker!!.invoke(null, solutionFeatures, submissionFeatures) } as List<String>
        } else {
            unwrap { featureChecker!!.invoke(null, language, solutionFeatures, submissionFeatures) } as List<String>
        }
    } else {
        listOf()
    }

    return TestResults.FeaturesComparison(errors)
}

class MaxLineCountExceeded(message: String) : RuntimeException(message)

fun Question.computeLineCounts(contents: String, language: Language): TestResults.LineCountComparison {
    val solutionLineCount = classification.lineCounts[language]
    check(solutionLineCount != null) { "Solution line count not available" }

    val maxLineCount = (control.maxLineCountMultiplier!! * solutionLineCount.source).toInt()

    val type = when (language) {
        Language.java -> Source.FileType.JAVA
        Language.kotlin -> Source.FileType.KOTLIN
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

fun Question.checkRecursion(
    klassName: String,
    language: Language,
    settings: Question.TestingSettings,
    isSolution: Boolean,
    results: TestResults,
): TestResults.RecursionComparison {

    val submissionRecursiveMethods = results.tests()!!.asSequence().filter {
        it.submissionResourceUsage!!.invokedRecursiveFunctions.isNotEmpty()
    }.map {
        it.jenisol!!.solutionExecutable
    }.filterIsInstance<Method>().map {
        ResourceMonitoringResults.MethodInfo(klassName, it.name, Type.getMethodDescriptor(it))
    }.toSet()

    val expectedRecursiveMethods = when {
        isSolution -> submissionRecursiveMethods
        else -> classification.recursiveMethodsByLanguage?.get(language) ?: settings.solutionRecursiveMethods?.get(
            language
        )
    }
    check(expectedRecursiveMethods != null)

    if (isSolution) {
        results.foundRecursiveMethods = expectedRecursiveMethods
    }

    val missingRecursiveMethods = (expectedRecursiveMethods - submissionRecursiveMethods).map { it.methodName }
    return TestResults.RecursionComparison(missingRecursiveMethods)
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
    return fun(stdin: List<String>, jeedFileSystem: Map<String, ByteArray?>, run: () -> Any?): CapturedResult {
        stdinStream.setInputs(stdin.map { "$it\n".toByteArray() })

        val outputListener = object : Sandbox.OutputListener {
            override fun stdout(int: Int) {
                stdinStream.bump()
            }

            override fun stderr(int: Int) {}
        }

        VirtualFilesystem.currentFilesystem = IsolatedFileSystemProvider().apply {
            fileSystem.populate(jeedFileSystem)
        }

        var resourceUsage: ResourceMonitoringCheckpoint? = null
        val jeedOutput = Sandbox.redirectOutput(outputListener, perTestOutputLimit, squashNormalOutput = true) {
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

private fun fixAccess(access: Int) = (access and Opcodes.ACC_PROTECTED.inv()) or Opcodes.ACC_PUBLIC

fun JeedClassLoader.fixProtectedAndPackagePrivate(): ClassLoader {
    val klasses = definedClasses.associateWith { className ->
        val klass = loadClass(className)
        val classesToOpen = klass.declaredClasses
            .filter { innerClass -> innerClass.isPackagePrivate() || innerClass.isProtected() }
            .map { innerClass -> innerClass.name }
        val fieldsToOpen = klass.declaredFields
            .filter { field -> !field.isSynthetic && field.isPackagePrivate() || field.isProtected() }
            .map { field -> field.name }
        val methodsToOpen = klass.declaredMethods
            .filter { method -> !method.isSynthetic && method.isPackagePrivate() || method.isProtected() }
            .map { method -> method.name }.toMutableSet()

        if (klass.enclosingClass != null && (klass.isProtected() || klass.isPackagePrivate())) {
            methodsToOpen += "<init>"
        }

        val classReader = ClassReader(bytecodeForClasses[className])
        val classWriter = ClassWriter(classReader, 0)

        val openingVisitor = object : ClassVisitor(Opcodes.ASM8, classWriter) {
            override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) =
                when {
                    name in classesToOpen -> super.visitInnerClass(name, outerName, innerName, fixAccess(access))
                    else -> super.visitInnerClass(name, outerName, innerName, access)
                }

            override fun visitField(
                access: Int,
                name: String,
                descriptor: String?,
                signature: String?,
                value: Any?
            ) = when (name) {
                in fieldsToOpen -> super.visitField(fixAccess(access), name, descriptor, signature, value)
                else -> super.visitField(access, name, descriptor, signature, value)
            }

            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ) = when (name) {
                in methodsToOpen -> super.visitMethod(fixAccess(access), name, descriptor, signature, exceptions)
                else -> super.visitMethod(access, name, descriptor, signature, exceptions)
            }
        }

        classReader.accept(openingVisitor, 0)
        classWriter.toByteArray()
    }

    return CopyableClassLoader(klasses, classLoader.parent)
}