package edu.illinois.cs.cs125.questioner.lib

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import edu.illinois.cs.cs125.jeed.core.CompilationArguments
import edu.illinois.cs.cs125.jeed.core.Features
import edu.illinois.cs.cs125.jeed.core.JeedFileManager
import edu.illinois.cs.cs125.jeed.core.LineCounts
import edu.illinois.cs.cs125.jeed.core.MutatedSource
import edu.illinois.cs.cs125.jeed.core.Mutation
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
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
@JsonClass(generateAdapter = true)
data class Question(
    val published: Published,
    val classification: Classification,
    var metadata: Metadata?,
    val annotatedControls: TestingControl,
    val question: FlatFile,
    val solutionByLanguage: Map<Language, FlatFile>,
    val alternativeSolutions: List<FlatFile>,
    val incorrectExamples: List<IncorrectFile>,
    val common: List<String>?,
    var commonFiles: List<CommonFile>?,
    val templateByLanguage: Map<Language, String>?,
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

    @JsonClass(generateAdapter = true)
    data class CorrectData(
        val path: String?,
        val name: String,
        val version: String,
        val author: String,
        val authorName: String,
        val description: String,
        val focused: Boolean,
        val publish: Boolean,
        val control: TestingControl
    )

    @JsonClass(generateAdapter = true)
    data class Published(
        val contentHash: String,
        val path: String,
        val author: String,
        val version: String,
        val name: String,
        val type: Type,
        val citation: Citation?,
        val packageName: String,
        val klass: String,
        val languages: Set<Language>,
        val descriptions: Map<Language, String>,
        val starters: Map<Language, String>?,
        val templateImports: Set<String>,
        val questionerVersion: String,
        val authorName: String,
        val tags: MutableSet<String> = mutableSetOf()
    )

    @JsonClass(generateAdapter = true)
    data class Classification(
        val featuresByLanguage: Map<Language, Features>,
        val lineCounts: Map<Language, LineCounts>,
        val complexity: Map<Language, Int>,
        var recursiveMethodsByLanguage: Map<Language, Set<ResourceMonitoringResults.MethodInfo>>? = null,
        var loadedClassesByLanguage: Map<Language, Set<String>>? = null,
    )

    @JsonClass(generateAdapter = true)
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

    @JsonClass(generateAdapter = true)
    data class TestingControl(
        val solutionThrows: Boolean?,
        val minTestCount: Int?,
        val maxTestCount: Int?,
        val minTimeout: Int?,
        val maxTimeout: Int?,
        val timeoutMultiplier: Double?,
        val minMutationCount: Int?,
        val maxMutationCount: Int?,
        val outputMultiplier: Double?,
        val maxExtraComplexity: Int?,
        val maxDeadCode: Int?,
        val maxExecutionCountMultiplier: Double?,
        val executionFailureMultiplier: Double?,
        val executionTimeoutMultiplier: Double?,
        val allocationFailureMultiplier: Double?,
        val allocationLimitMultiplier: Double?,
        val minExtraSourceLines: Int?,
        val sourceLinesMultiplier: Double?,
        val seed: Int?,
        val maxComplexityMultiplier: Double?,
        val maxLineCountMultiplier: Double?,
        val maxClassSizeMultiplier: Double?,
        val initialTestingDelay: Int?,
        val canTestTest: Boolean?,
        val fullDesignErrors: Boolean?,
        val cpuTimeoutMultiplier: Double?
    ) {
        companion object {
            const val DEFAULT_SOLUTION_THROWS = false
            const val DEFAULT_MIN_TEST_COUNT = 64
            const val DEFAULT_MAX_TEST_COUNT = 1024
            const val DEFAULT_MIN_TIMEOUT = 128
            const val DEFAULT_MAX_TIMEOUT = 2048
            const val DEFAULT_TIMEOUT_MULTIPLIER = 8.0
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
            const val DEFAULT_SEED = -1
            const val DEFAULT_MAX_COMPLEXITY_MULTIPLIER = 8.0
            const val DEFAULT_MAX_LINECOUNT_MULTIPLIER = 8.0
            const val DEFAULT_MAX_CLASSSIZE_MULTIPLIER = 8.0
            const val DEFAULT_MIN_FAIL_FAST_COMPLEXITY = 16
            const val DEFAULT_MIN_FAIL_FAST_CLASS_SIZE_MULTIPLIER = 16
            const val DEFAULT_MAX_EXECUTION_COUNT: Long = DEFAULT_MAX_TIMEOUT.toLong() * 1024 * 1024
            const val DEFAULT_INITIAL_TESTING_DELAY: Int = 0
            const val DEFAULT_CAN_TESTTEST: Boolean = true
            const val DEFAULT_FULL_DESIGN_ERRORS: Boolean = false
            const val DEFAULT_CPU_TIMEOUT_MULTIPLIER: Double = 8.0

            val DEFAULTS = TestingControl(
                DEFAULT_SOLUTION_THROWS,
                DEFAULT_MIN_TEST_COUNT,
                DEFAULT_MAX_TEST_COUNT,
                DEFAULT_MIN_TIMEOUT,
                DEFAULT_MAX_TIMEOUT,
                DEFAULT_TIMEOUT_MULTIPLIER,
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
                DEFAULT_INITIAL_TESTING_DELAY,
                DEFAULT_CAN_TESTTEST,
                DEFAULT_FULL_DESIGN_ERRORS,
                DEFAULT_CPU_TIMEOUT_MULTIPLIER
            )
        }
    }

    val control: TestingControl by lazy {
        TestingControl.DEFAULTS merge annotatedControls
    }

    @JsonClass(generateAdapter = true)
    data class TestingSettings(
        val seed: Int,
        val testCount: Int = -1,
        val timeout: Int,
        val outputLimit: Int,
        val perTestOutputLimit: Int,
        val javaWhitelist: Set<String>?,
        val kotlinWhitelist: Set<String>?,
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
        val wallTime: LanguagesResourceUsage? = null,
        val cpuTime: LanguagesResourceUsage? = null,
        val runAll: Boolean = false
    )

    @JsonClass(generateAdapter = true)
    data class TestTestingSettings(
        val shortCircuit: Boolean? = null,
        val limit: Int? = null,
        val selectionStrategy: SelectionStrategy? = null,
        val seed: Long? = null
    ) {
        enum class SelectionStrategy {
            HARDEST, EASIEST, EVENLY_SPACED
        }

        companion object {
            val DEFAULTS = TestTestingSettings(false, Int.MAX_VALUE, SelectionStrategy.EVENLY_SPACED, null)
        }
    }

    @JsonClass(generateAdapter = true)
    data class TestTestingLimits(
        val timeout: Int,
        val outputLimit: Int,
        val executionCountLimit: LanguagesResourceUsage,
        val allocationLimit: LanguagesResourceUsage,
        val wallTime: LanguagesResourceUsage? = null,
        val cpuTime: LanguagesResourceUsage? = null
    )

    @JsonClass(generateAdapter = true)
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
        val solutionMaxClassSize: LanguagesResourceUsage? = null,
        val canTestTest: Boolean = false
    )

    @JsonClass(generateAdapter = true)
    data class LanguagesResourceUsage(val java: Long, val kotlin: Long? = null) {
        operator fun get(language: Language): Long = when (language) {
            Language.java -> java
            Language.kotlin -> kotlin!!
        }
    }

    @JsonClass(generateAdapter = true)
    data class LanguagesSolutionClassSize(
        val java: Int,
        val kotlin: Int? = null
    )

    @JsonClass(generateAdapter = true)
    data class Citation(val source: String, val link: String? = null)

    @JsonClass(generateAdapter = true)
    data class FlatFile(
        val klass: String,
        val contents: String,
        val language: Language,
        val path: String?,
        val complexity: Int? = null,
        val features: Features? = null,
        val lineCount: LineCounts? = null,
        val expectedDeadCount: Int? = null,
        val suppressions: Set<String> = setOf()
    )

    @JsonClass(generateAdapter = true)
    data class CommonFile(
        val klass: String,
        val contents: String,
        val language: Language
    )

    @JsonClass(generateAdapter = true)
    data class IncorrectFile(
        val klass: String,
        val contents: String,
        val reason: Reason,
        val language: Language,
        val path: String?,
        val starter: Boolean,
        var needed: Boolean = true,
        var testCount: Int = -1,
        val suppressions: Set<String> = setOf(),
        @Transient
        val mutation: MutatedSource? = null
    ) {
        @Suppress("SpellCheckingInspection")
        enum class Reason {
            DESIGN, COMPILE, TEST, CHECKSTYLE, KTLINT, TIMEOUT, DEADCODE, LINECOUNT, TOOLONG, MEMORYLIMIT, RECURSION, COMPLEXITY, FEATURES, TOOMUCHOUTPUT, MEMOIZATION, CLASSSIZE
        }
    }

    @JsonClass(generateAdapter = true)
    data class TestTestingMutation(
        val deltas: List<String>,
        val language: Language,
        val incorrectIndex: Int?,
        val mutation: Mutation.Type?,
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
            val source = question.contentsToSource(contents(question), language, results)

            return when (language) {
                Language.java ->
                    question.compileSubmission(
                        source,
                        InvertingClassLoader(setOf(question.published.klass)),
                        results,
                        suppressions
                    )

                Language.kotlin ->
                    question.kompileSubmission(
                        source,
                        InvertingClassLoader(setOf(question.published.klass, "${question.published.klass}Kt")),
                        results,
                        suppressions
                    )
            }.let {
                TestTestingSource(contents(question), question.fixTestingMethods(it.classLoader))
            }
        }
    }

    data class CompiledCommon(val classLoader: ClassLoader, val fileManager: JeedFileManager)

    @delegate:Transient
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

    @delegate:Transient
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

    data class TestTestingSource(val contents: String, val classloader: ClassLoader)

    @delegate:Transient
    val compiledSolutionForTesting by lazy {
        try {
            Source(mapOf("${question.klass}.java" to question.contents)).let { questionSource ->
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
            }.let {
                TestTestingSource(question.contents, fixTestingMethods(it.classLoader))
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

    @delegate:Transient
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

    @delegate:Transient
    val solution by lazy {
        jenisol(compiledSolution.classLoader.loadClass(published.klass))
    }

    @delegate:Transient
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
        // warm
        compiledCommon
        compiledSolution
        compiledSolutionForTesting
        compilationDefinedClass
        solution
        featureChecker
    }

    var correctPath: String? = null

    @Transient
    var testingCount: Int = 0

    fun cleanForUpload() {
        correctPath = null
        testingCount = 0
        metadata = null
    }

    @Transient
    val fullPath = "${published.author}/${published.path}/${published.version}/${published.contentHash}"
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

fun Collection<Question>.toJSON(): String =
    moshi.adapter<List<Question>>(Types.newParameterizedType(List::class.java, Question::class.java))
        .toJson(this.toList())


fun File.loadQuestionList() =
    moshi.adapter<List<Question>>(Types.newParameterizedType(List::class.java, Question::class.java))
        .fromJson(readText())!!

fun File.loadQuestion() = try {
    moshi.adapter(Question::class.java).fromJson(readText())!!
} catch (e: Exception) {
    null
}

fun Question.writeToFile(file: File) = try {
    file.writeText(moshi.adapter(Question::class.java).indent("  ").toJson(this))
} catch (e: Exception) {
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