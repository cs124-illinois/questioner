package edu.illinois.cs.cs125.questioner.lib

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import edu.illinois.cs.cs125.jeed.core.CompilationArguments
import edu.illinois.cs.cs125.jeed.core.Features
import edu.illinois.cs.cs125.jeed.core.JeedFileManager
import edu.illinois.cs.cs125.jeed.core.KompilationArguments
import edu.illinois.cs.cs125.jeed.core.LineCounts
import edu.illinois.cs.cs125.jeed.core.MutatedSource
import edu.illinois.cs.cs125.jeed.core.Mutation
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.kompile
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import java.io.File
import java.lang.reflect.ReflectPermission
import java.util.PropertyPermission
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import edu.illinois.cs.cs125.jenisol.core.solution as jenisol

private val sharedClassWhitelist = setOf(
    "java.lang.",
    "java.io.PrintStream",
    "kotlin.Metadata",
    "kotlin.reflect.jvm.",
    "java.util.Iterator",
    "java.util.Collection"
)

@Suppress("EnumNaming", "EnumEntryName")
enum class Language { java, kotlin }

@Suppress("MemberVisibilityCanBePrivate", "LargeClass", "TooManyFunctions")
@Serializable
data class Question(
    val published: Published,
    val classification: Classification,
    var metadata: Metadata? = null,
    val annotatedControls: TestingControl = TestingControl(),
    val question: FlatFile,
    val solutionByLanguage: Map<Language, FlatFile>,
    val alternativeSolutions: List<FlatFile> = listOf(),
    val incorrectExamples: List<IncorrectFile> = listOf(),
    val common: List<String>? = null,
    var commonFiles: List<CommonFile>? = null,
    val templateByLanguage: Map<Language, String>? = null,
    val importWhitelist: Set<String>,
    val importBlacklist: Set<String>,
    @Suppress("unused")
    val checkstyleSuppressions: Set<String> = setOf()
) {

    // TODO: Remove after migration
    init {
        if (common != null && commonFiles == null) {
            commonFiles = common.mapIndexed { i, content ->
                CommonFile("Common$i", content, Language.java)
            }
        }
    }

    @Transient
    val hasKotlin = published.languages.contains(Language.kotlin)

    fun getTemplate(language: Language) = templateByLanguage?.get(language)

    fun getSolution(language: Language) = solutionByLanguage[language]

    fun getCorrect(language: Language) = solutionByLanguage[language]?.contents

    enum class Type { KLASS, METHOD, SNIPPET }

    @Serializable
    data class CorrectData(
        val path: String? = null,
        val name: String,
        val version: String,
        val author: String,
        val authorName: String,
        val description: String,
        val focused: Boolean,
        val publish: Boolean,
        val control: TestingControl
    )

    @Serializable
    data class Published(
        val contentHash: String,
        val path: String,
        val author: String,
        val version: String,
        val name: String,
        val type: Type,
        val citation: Citation? = null,
        val packageName: String,
        val klass: String,
        val languages: Set<Language>,
        val descriptions: Map<Language, String>,
        val starters: Map<Language, String>? = null,
        val templateImports: Set<String> = setOf(),
        val questionerVersion: String,
        val authorName: String,
        val tags: MutableSet<String> = mutableSetOf(),
        val kotlinImports: Set<String>? = null,
        val javaTestingImports: Set<String>? = null,
        val kotlinTestingImports: Set<String>? = null
    )

    @Serializable
    data class Classification(
        val featuresByLanguage: Map<Language, Features>,
        val lineCounts: Map<Language, LineCounts>,
        val complexity: Map<Language, Int>,
        var recursiveMethodsByLanguage: Map<Language, Set<ResourceMonitoringResults.MethodInfo>>? = null,
        var loadedClassesByLanguage: Map<Language, Set<String>>? = null,
    )

    @Serializable
    data class Metadata(
        val allFiles: Set<String> = setOf(),
        val unusedFiles: Set<String> = setOf(),
        val focused: Boolean? = null,
        val publish: Boolean? = null
    ) {
        companion object {
            const val DEFAULT_FOCUSED = false
            const val DEFAULT_PUBLISH = true
        }
    }

    @Serializable
    data class TestingControl(
        val solutionThrows: Boolean? = null,
        val minTestCount: Int? = null,
        val maxTestCount: Int? = null,
        val timeoutMultiplier: Double? = null,
        val testTestingtimeoutMultiplier: Double? = null,
        val minMutationCount: Int? = null,
        val maxMutationCount: Int? = null,
        val outputMultiplier: Double? = null,
        val maxExtraComplexity: Int? = null,
        val maxDeadCode: Int? = null,
        val maxExecutionCountMultiplier: Double? = null,
        val executionFailureMultiplier: Double? = null,
        val executionTimeoutMultiplier: Double? = null,
        val allocationFailureMultiplier: Double? = null,
        val allocationLimitMultiplier: Double? = null,
        val minExtraSourceLines: Int? = null,
        val sourceLinesMultiplier: Double? = null,
        val seed: Int? = null,
        val maxComplexityMultiplier: Double? = null,
        val maxLineCountMultiplier: Double? = null,
        val maxClassSizeMultiplier: Double? = null,
        val questionerWarmTimeoutMultiplier: Double? = null,
        val canTestTest: Boolean? = null,
        val fullDesignErrors: Boolean? = null
    ) {
        companion object {
            const val DEFAULT_SOLUTION_THROWS = false
            const val DEFAULT_MIN_TEST_COUNT = 64
            const val DEFAULT_MAX_TEST_COUNT = 1024
            const val DEFAULT_TIMEOUT_MULTIPLIER = 1.0
            const val DEFAULT_TESTTESTING_TIMEOUT_MULTIPLIER = 1.0
            const val DEFAULT_MIN_MUTATION_COUNT = 0
            const val DEFAULT_OUTPUT_MULTIPLIER = 8.0
            const val DEFAULT_MAX_EXTRA_COMPLEXITY = 2
            const val DEFAULT_MAX_DEAD_CODE = 0
            const val DEFAULT_MAX_EXECUTION_COUNT_MULTIPLIER = 256.0
            const val DEFAULT_EXECUTION_COUNT_FAILURE_MULTIPLIER = 4.0
            const val DEFAULT_EXECUTION_COUNT_TIMEOUT_MULTIPLIER = 16.0
            const val DEFAULT_ALLOCATION_FAILURE_MULTIPLIER = 4.0
            const val DEFAULT_ALLOCATION_LIMIT_MULTIPLIER = 16.0
            const val DEFAULT_MIN_EXTRA_SOURCE_LINES = 2
            const val DEFAULT_SOURCE_LINES_MULTIPLIER = 1.5
            const val DEFAULT_SEED = 124
            const val DEFAULT_MAX_COMPLEXITY_MULTIPLIER = 8.0
            const val DEFAULT_MAX_LINECOUNT_MULTIPLIER = 8.0
            const val DEFAULT_MAX_CLASSSIZE_MULTIPLIER = 8.0
            const val DEFAULT_MIN_FAIL_FAST_COMPLEXITY = 16
            const val DEFAULT_MIN_FAIL_FAST_CLASS_SIZE_MULTIPLIER = 16
            const val DEFAULT_MAX_EXECUTION_COUNT: Long = 2048L * 1024L * 1024L
            const val DEFAULT_QUESTION_WARM_TIMEOUT_MULTIPLIER: Double = 1.0
            const val DEFAULT_CAN_TESTTEST: Boolean = true
            const val DEFAULT_FULL_DESIGN_ERRORS: Boolean = false

            val DEFAULTS = TestingControl(
                DEFAULT_SOLUTION_THROWS,
                DEFAULT_MIN_TEST_COUNT,
                DEFAULT_MAX_TEST_COUNT,
                DEFAULT_TIMEOUT_MULTIPLIER,
                DEFAULT_TESTTESTING_TIMEOUT_MULTIPLIER,
                DEFAULT_MIN_MUTATION_COUNT,
                null,
                DEFAULT_OUTPUT_MULTIPLIER,
                DEFAULT_MAX_EXTRA_COMPLEXITY,
                DEFAULT_MAX_DEAD_CODE,
                DEFAULT_MAX_EXECUTION_COUNT_MULTIPLIER,
                DEFAULT_EXECUTION_COUNT_FAILURE_MULTIPLIER,
                DEFAULT_EXECUTION_COUNT_TIMEOUT_MULTIPLIER,
                DEFAULT_ALLOCATION_FAILURE_MULTIPLIER,
                DEFAULT_ALLOCATION_LIMIT_MULTIPLIER,
                DEFAULT_MIN_EXTRA_SOURCE_LINES,
                DEFAULT_SOURCE_LINES_MULTIPLIER,
                DEFAULT_SEED,
                DEFAULT_MAX_COMPLEXITY_MULTIPLIER,
                DEFAULT_MAX_LINECOUNT_MULTIPLIER,
                DEFAULT_MAX_CLASSSIZE_MULTIPLIER,
                DEFAULT_QUESTION_WARM_TIMEOUT_MULTIPLIER,
                DEFAULT_CAN_TESTTEST,
                DEFAULT_FULL_DESIGN_ERRORS
            )
        }
    }

    val control: TestingControl by lazy {
        TestingControl.DEFAULTS merge annotatedControls
    }

    @Serializable
    data class TestingSettings(
        val seed: Int,
        val testCount: Int = -1,
        val outputLimit: Int,
        val perTestOutputLimit: Int,
        val javaWhitelist: Set<String>? = null,
        val kotlinWhitelist: Set<String>? = null,
        val shrink: Boolean,
        val executionCountLimit: LanguagesResourceUsage,
        val allocationLimit: LanguagesResourceUsage? = null,
        var solutionCoverage: TestResults.CoverageComparison.LineCoverage? = null,
        var solutionClassSize: LanguagesResourceUsage? = null,
        var solutionExecutionCount: LanguagesResourceUsage? = null,
        var solutionAllocation: LanguagesResourceUsage? = null,
        var solutionDeadCode: LanguagesResourceUsage? = null,
        val checkBlacklist: Boolean = true,
        val disableLineCountLimit: Boolean = false,
        val disableAllocationLimit: Boolean = false,
        var solutionRecursiveMethods: Map<Language, Set<ResourceMonitoringResults.MethodInfo>>? = null,
        val minTestCount: Int = -1,
        val maxTestCount: Int = -1,
        val suppressions: Set<String>? = null,
        val kotlinSuppressions: Set<String>? = null,
        val runAll: Boolean = false,
        val recordTrace: Boolean = false,
        val followTrace: List<Int>? = null,
        val timeoutMultiplier: Double? = null,
        val solutionOutputAmount: Int? = null,
    )

    @Serializable
    data class TestTestingSettings(
        val shortCircuit: Boolean? = null,
        val limit: Int? = null,
        val selectionStrategy: SelectionStrategy? = null,
        val seed: Long? = null
    ) {
        enum class SelectionStrategy {
            EASIEST, HARDEST, EVENLY_SPACED, EASIEST_AND_HARDEST
        }

        companion object {
            val DEFAULTS = TestTestingSettings(
                false,
                Int.MAX_VALUE,
                SelectionStrategy.EASIEST_AND_HARDEST,
                null
            )
        }
    }

    @Serializable
    data class TestTestingLimits(
        val outputLimit: Int,
        val executionCountLimit: LanguagesResourceUsage,
        val allocationLimit: LanguagesResourceUsage
    )

    @Serializable
    data class ValidationResults(
        val seed: Int,
        val requiredTestCount: Int,
        val mutationCount: Int,
        val solutionMaxRuntime: Int,
        val bootstrapLength: Long,
        val mutationLength: Long,
        val incorrectLength: Long,
        val calibrationLength: Long,
        val solutionCoverage: TestResults.CoverageComparison.LineCoverage,
        val executionCounts: LanguagesResourceUsage,
        val memoryAllocation: LanguagesResourceUsage,
        val outputAmount: Int? = null,
        val solutionMaxClassSize: LanguagesResourceUsage? = null,
        val canTestTest: Boolean = false,
        val testTestingIncorrectCount: Map<Language, Int>? = null,
        // Temporary: allocation records for debugging memory discrepancies
        val solutionAllocations: Map<Language, List<AllocationRecord>>? = null,
        // Temporary: memory breakdown for debugging memory discrepancies
        val solutionMemoryBreakdown: Map<Language, TestResults.MemoryBreakdown>? = null
    )

    @Serializable
    data class LanguagesResourceUsage(val java: Long, val kotlin: Long? = null) {
        operator fun get(language: Language): Long = when (language) {
            Language.java -> java
            Language.kotlin -> kotlin!!
        }

        companion object {
            fun both(both: Long) = LanguagesResourceUsage(both, both)
        }
    }

    @Serializable
    data class LanguagesSolutionClassSize(
        val java: Int,
        val kotlin: Int? = null
    )

    @Serializable
    data class Citation(val source: String, val link: String? = null)

    @Serializable
    data class FlatFile(
        val klass: String,
        val contents: String,
        val language: Language,
        val path: String? = null,
        val complexity: Int? = null,
        val features: Features? = null,
        val lineCount: LineCounts? = null,
        val expectedDeadCount: Int? = null,
        val suppressions: Set<String> = setOf()
    )

    @Serializable
    data class CommonFile(
        val klass: String,
        val contents: String,
        val language: Language
    )

    @Serializable
    data class IncorrectFile(
        val klass: String,
        val contents: String,
        val reason: Reason,
        val language: Language,
        var path: String? = null,
        val starter: Boolean,
        var needed: Boolean = true,
        var testCount: Int = -1,
        val suppressions: Set<String> = setOf(),
        @Transient
        val mutation: MutatedSource? = null,
        val mutationSourceLanguage: Language? = null
    ) {
        @Suppress("SpellCheckingInspection")
        enum class Reason {
            DESIGN, COMPILE, TEST, CHECKSTYLE, KTLINT, TIMEOUT, DEADCODE, LINECOUNT, TOOLONG, MEMORYLIMIT, RECURSION, COMPLEXITY, FEATURES, MEMOIZATION, CLASSSIZE, EXTRAOUTPUT
        }
    }

    @Serializable
    data class TestTestingMutation(
        val deltas: List<String>,
        val language: Language,
        val incorrectIndex: Int? = null,
        val mutation: Mutation.Type? = null,
        val testCount: Int,
        val suppressions: Set<String> = setOf()
    ) {

        @Transient
        private var _contents: String? = null
        fun contents(question: Question): String {
            if (_contents != null) {
                return _contents!!
            }
            return question.getCorrect(language)!!.let {
                DiffUtils.patch(it.lines(), UnifiedDiffUtils.parseUnifiedDiff(deltas))
            }.joinToString("\n").also {
                _contents = it
            }
        }

        suspend fun compiled(question: Question): TestTestingSource {
            val results = TestResults(language)
            val questionContents = contents(question)
            val source = question.contentsToSource(questionContents, language, results)

            return when (language) {
                Language.java ->
                    question.compileSubmission(
                        source,
                        InvertingClassLoader(setOf(question.published.klass)),
                        results,
                        suppressions,
                    )

                Language.kotlin ->
                    question.kompileSubmission(
                        source,
                        InvertingClassLoader(setOf(question.published.klass, "${question.published.klass}Kt")),
                        results,
                        suppressions
                    )
            }.let { compiledSource ->
                val (newClassLoader, newFileManager) = question.fixTestingMethods(compiledSource, language)
                TestTestingSource(source.contents, newClassLoader, newFileManager)
            }
        }
    }

    data class CompiledCommon(val classLoader: ClassLoader, val fileManager: JeedFileManager)

    val compiledCommon by lazy {
        commonFiles?.associate { "${it.klass}.java" to it.contents }?.let { commonMap ->
            if (commonMap.isNotEmpty()) {
                Source(commonMap)
                    .compile(CompilationArguments(isolatedClassLoader = true, parameters = true))
                    .let { compiledSource ->
                        CompiledCommon(
                            compiledSource.classLoader.fixProtectedAndPackagePrivate(),
                            compiledSource.fileManager
                        )
                    }
            } else {
                null
            }
        }
    }

    val compiledSolution by lazy {
        Source(mapOf("${question.klass}.java" to question.contents)).let { questionSource ->
            try {
                if (compiledCommon == null) {
                    questionSource.compile(
                        CompilationArguments(isolatedClassLoader = true, parameters = true)
                    )
                } else {
                    questionSource.compile(
                        CompilationArguments(
                            parentClassLoader = compiledCommon!!.classLoader,
                            parentFileManager = compiledCommon!!.fileManager,
                            parameters = true,
                        )
                    )
                }
            } catch (e: Exception) {
                System.err.println(
                    """
Failed compiling solution control class:
---
${question.contents}
---"""
                )
                throw e
            }
        }
    }

    data class TestTestingSource(val contents: String, val classloader: ClassLoader, val fileManager: JeedFileManager)

    val javaSolutionForTesting by lazy {
        // End-of-source comment to prevent cache collisions on actual solution but still enable caching
        val contents = """${question.contents}
// Java solution for testing
"""
        try {
            Source(mapOf("${question.klass}.java" to contents)).let { questionSource ->
                if (compiledCommon == null) {
                    questionSource.compile(
                        CompilationArguments(isolatedClassLoader = true, parameters = true)
                    )
                } else {
                    questionSource.compile(
                        CompilationArguments(
                            parentClassLoader = compiledCommon!!.classLoader,
                            parentFileManager = compiledCommon!!.fileManager,
                            parameters = true,
                        )
                    )
                }
            }.let { compiledSource ->
                val (newClassLoader, newFileManager) = fixTestingMethods(compiledSource, Language.java)
                TestTestingSource(question.contents, newClassLoader, newFileManager)

            }
        } catch (e: Exception) {
            System.err.println(
                """
Failed compiling Java solution:
---
$contents
---"""
            )
            throw e
        }
    }

    val kotlinSolutionForTesting by lazy {
        if (solutionByLanguage[Language.kotlin] == null) {
            null
        } else {
            // End-of-source comment to prevent cache collisions on actual solution but still enable caching
            val contents =
                """${templateSubmission(solutionByLanguage[Language.kotlin]!!.contents, Language.kotlin).contents}
// Kotlin solution for testing
"""
            try {
                Source(mapOf("${question.klass}.kt" to contents)).let { questionSource ->
                    if (compiledCommon == null) {
                        questionSource.kompile(
                            KompilationArguments(isolatedClassLoader = true, parameters = true)
                        )
                    } else {
                        questionSource.kompile(
                            KompilationArguments(
                                parentClassLoader = compiledCommon!!.classLoader,
                                parentFileManager = compiledCommon!!.fileManager,
                                parameters = true,
                            )
                        )
                    }
                }.let { compiledSource ->
                    val (newClassLoader, newFileManager) = fixTestingMethods(compiledSource, Language.kotlin)
                    TestTestingSource(contents, newClassLoader, newFileManager)
                }
            } catch (e: Exception) {
                System.err.println(
                    """
Failed compiling Kotlin solution:
---
$contents
---"""
                )
                throw e
            }
        }
    }

    val compilationDefinedClass by lazy {
        compiledSolution.classLoader.definedClasses.topLevelClasses().let {
            require(it.size == 1)
            it.first()
        }.also {
            require(it == published.klass) {
                "Solution defined a name that is different from the parsed name: $it != $published.klass"
            }
        }
    }

    val solution by lazy {
        jenisol(compiledSolution.classLoader.loadClass(published.klass))
    }

    val featureChecker by lazy {
        compiledSolution.classLoader.loadClass(published.klass).declaredMethods.filter { it.isCheckFeatures() }.let {
            require(it.size <= 1) { "Can only use @CheckFeatures once" }
            it.firstOrNull()
        }?.also {
            CheckFeatures.validate(it)
        }
    }

    fun Set<String>.topLevelClasses() = map { it.split("$").first() }.distinct()

    fun Language.extension() = when (this) {
        Language.java -> "java"
        Language.kotlin -> "kt"
    }

    @Transient
    val defaultJavaClassWhitelist = importWhitelist.toMutableSet().also {
        it.addAll(sharedClassWhitelist)
    }.toSet()

    @Transient
    val defaultKotlinClassWhitelist = importWhitelist.toMutableSet().also {
        it.addAll(sharedClassWhitelist)
        it.addAll(setOf("java.util.", "kotlin."))
    }.toSet()

    var testingSettings: TestingSettings? = null
    var testTestingLimits: TestTestingLimits? = null

    var validationResults: ValidationResults? = null

    val validated: Boolean
        get() = testingSettings != null

    val testTestingValidated: Boolean
        get() = testTestingLimits != null

    var fauxStatic: Boolean = false

    var testTestingIncorrect: List<TestTestingMutation>? = null
    suspend fun compileAllTestTestingIncorrect() = testTestingIncorrect!!.forEach { validationMutation ->
        validationMutation.compiled(this)
    }

    fun filename(language: Language) = "${published.klass}.${language.extension()}"

    fun testFilename(language: Language) = "Test${published.klass}.${language.extension()}"

    @Suppress("unused")
    companion object {
        const val DEFAULT_RETURN_TIMEOUT = 16
        const val MAX_START_MULTIPLE_COUNT = 128
        const val UNLIMITED_OUTPUT_LINES = 102400
        const val MIN_PER_TEST_LINES = 1024
        const val DEFAULT_MAX_OUTPUT_SIZE = 8 * 1024 * 1024
        const val TESTING_PRIORITY = Thread.NORM_PRIORITY + 2

        @Transient
        val SAFE_PERMISSIONS =
            setOf(
                RuntimePermission("accessDeclaredMembers"),
                ReflectPermission("suppressAccessChecks"),
                RuntimePermission("getClassLoader"),
                RuntimePermission("localeServiceProvider"),
                RuntimePermission("charsetProvider"),
                PropertyPermission("*", "read")
            )
    }

    fun warm() {
        compiledCommon
        compiledSolution
        compilationDefinedClass
        solution
        featureChecker
    }

    fun warmTest() {
        compiledCommon
        compiledSolution
        javaSolutionForTesting
        kotlinSolutionForTesting
        compilationDefinedClass
        solution
        featureChecker
    }

    var correctPath: String? = null

    @Transient
    var solveCount: Int = 0

    @Transient
    var testedCount: Int = 0

    fun cleanForUpload() {
        correctPath = null
        metadata = null
        solveCount = 0
        testedCount = 0
    }

    @Transient
    val fullPath = "${published.author}/${published.path}/${published.version}/${published.contentHash}"

    @Transient
    val usefulPath = "${published.author}/${published.path}/${published.version}"
}

fun String.deTemplate(template: String?): String {
    return when (template) {
        null -> this
        else -> {
            val lines = split("\n")
            val start = lines.indexOfFirst { it.contains("TEMPLATE_START") }
            val end = lines.indexOfFirst { it.contains("TEMPLATE_END") }
            require(start != -1) { "Couldn't locate TEMPLATE_START during extraction" }
            require(end != -1) { "Couldn't locate TEMPLATE_END during extraction" }
            lines.slice((start + 1) until end).joinToString("\n").trimIndent()
        }
    }
}

fun Collection<Question>.toJSON(): String = json.encodeToString(this.toList())

fun File.loadQuestionList(): List<Question> = json.decodeFromString(readText())

fun File.loadQuestion() = try {
    json.decodeFromString<Question>(readText())
} catch (_: Exception) {
    null
}

fun Question.writeToFile(file: File) = try {
    file.writeText(json.encodeToString(this))
} catch (_: Exception) {
    null
}

inline infix fun <reified T : Any> T.merge(other: T?): T {
    if (other == null) {
        return this
    }
    val nameToProperty = T::class.declaredMemberProperties.associateBy { it.name }
    val primaryConstructor = T::class.primaryConstructor!!
    val args = primaryConstructor.parameters.associateWith { parameter ->
        val property = nameToProperty[parameter.name]!!
        (property.get(other) ?: property.get(this))
    }
    return primaryConstructor.callBy(args)
}

fun <T> makeLanguageMap(java: T?, kotlin: T?) = if (java != null) {
    mutableMapOf(Language.java to java).apply {
        if (kotlin != null) {
            put(Language.kotlin, kotlin)
        }
    }.toMap()
} else {
    null
}