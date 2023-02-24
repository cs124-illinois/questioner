package edu.illinois.cs.cs125.questioner.lib

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import edu.illinois.cs.cs125.jeed.core.*
import edu.illinois.cs.cs125.questioner.lib.moshi.Adapters
import java.io.File
import java.lang.reflect.ReflectPermission
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import edu.illinois.cs.cs125.jeed.core.moshi.Adapters as JeedAdapters
import edu.illinois.cs.cs125.jenisol.core.solution as jenisol

val moshi: Moshi = Moshi.Builder().apply {
    Adapters.forEach { add(it) }
    JeedAdapters.forEach { add(it) }
}.build()

private val sharedClassWhitelist = setOf(
    "java.lang.",
    "java.io.PrintStream",
    "kotlin.Metadata",
    "kotlin.reflect.jvm.",
    "java.util.Iterator",
    "java.util.Collection"
)

@Suppress("MemberVisibilityCanBePrivate", "LargeClass", "TooManyFunctions")
@JsonClass(generateAdapter = true)
data class Question(
    val name: String,
    val type: Type,
    val klass: String,
    val metadata: Metadata,
    val annotatedControls: TestingControl,
    val question: FlatFile,
    val correct: FlatFile,
    val alternativeSolutions: List<FlatFile>,
    val incorrect: List<IncorrectFile>,
    val common: List<String>?,
    var javaTemplate: String?,
    var kotlinTemplate: String?,
    val importWhitelist: Set<String>,
    val importBlacklist: Set<String>,
    val checkstyleSuppressions: Set<String>,
    val slug: String,
    val kotlinSolution: FlatFile?,
    val detemplatedJavaStarter: String? = incorrect.find { it.language == Language.java && it.starter }?.contents,
    val detemplatedKotlinStarter: String? = incorrect.find { it.language == Language.kotlin && it.starter }?.contents,
    val hasKotlin: Boolean =
        metadata.kotlinDescription != null && kotlinSolution != null &&
            ((detemplatedJavaStarter != null) == (detemplatedKotlinStarter != null)),
    val published: Published = Published(
        name,
        type,
        slug,
        metadata.author,
        metadata.version,
        metadata.citation,
        metadata.packageName,
        mutableSetOf(Language.java).apply {
            if (hasKotlin) {
                add(Language.kotlin)
            }
        }.toSet(),
        mutableMapOf(Language.java to metadata.javaDescription).apply {
            if (hasKotlin) {
                put(Language.kotlin, metadata.kotlinDescription!!)
            }
        }.toMap(),
        detemplatedJavaStarter?.let {
            mutableMapOf(Language.java to detemplatedJavaStarter).apply {
                if (hasKotlin) {
                    put(Language.kotlin, detemplatedKotlinStarter!!)
                }
            }.toMap()
        },
        mutableMapOf(Language.java to correct.complexity!!).apply {
            if (hasKotlin) {
                put(
                    Language.kotlin,
                    alternativeSolutions
                        .filter { it.language == Language.kotlin }
                        .mapNotNull { it.complexity }
                        .minOrNull()!!
                )
            }
        },
        mutableMapOf(Language.java to correct.features!!).apply {
            if (hasKotlin) {
                put(Language.kotlin, kotlinSolution!!.features!!)
            }
        },
        mutableMapOf(Language.java to correct.lineCount!!).apply {
            if (hasKotlin) {
                put(Language.kotlin, kotlinSolution!!.lineCount!!)
            }
        },
        metadata.templateImports
    )
) {
    @Transient
    val correctByLanguage = mutableMapOf(Language.java to correct.contents).apply {
        if (hasKotlin) {
            put(Language.kotlin, alternativeSolutions.find { it.language == Language.kotlin }!!.contents)
        }
    }

    @Suppress("EnumNaming", "EnumEntryName")
    enum class Language { java, kotlin }

    enum class Type { KLASS, METHOD, SNIPPET }

    @JsonClass(generateAdapter = true)
    data class CorrectData(
        val path: String?,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val focused: Boolean,
        val control: TestingControl
    )

    @JsonClass(generateAdapter = true)
    data class Published(
        val name: String,
        val type: Type,
        val path: String,
        val author: String,
        val version: String,
        val citation: Citation?,
        val packageName: String,
        val languages: Set<Language>,
        val descriptions: Map<Language, String>,
        val starters: Map<Language, String>?,
        val complexity: Map<Language, Int>,
        val features: Map<Language, Features>,
        val lineCounts: Map<Language, LineCounts>,
        val templateImports: Set<String>,
        var validationResults: ValidationResults? = null
    )

    @JsonClass(generateAdapter = true)
    data class Metadata(
        val contentHash: String,
        val packageName: String,
        val version: String,
        val author: String,
        val javaDescription: String,
        val kotlinDescription: String?,
        val citation: Citation?,
        val usedFiles: List<String> = listOf(),
        val templateImports: Set<String> = setOf(),
        val focused: Boolean? = null
    ) {
        companion object {
            const val DEFAULT_FOCUSED = false
        }
    }

    @JsonClass(generateAdapter = true)
    data class TestingControl(
        val solutionThrows: Boolean?,
        val minTestCount: Int?,
        val maxTestCount: Int?,
        val minTimeout: Int?,
        val maxTimeout: Int?,
        val timeoutMultiplier: Int?,
        val minMutationCount: Int?,
        val maxMutationCount: Int?,
        val outputMultiplier: Int?,
        val maxExtraComplexity: Int?,
        val maxDeadCode: Int?,
        val maxExecutionCountMultiplier: Long?,
        val executionFailureMultiplier: Int?,
        val executionTimeoutMultiplier: Int?,
        val allocationFailureMultiplier: Int?,
        val allocationLimitMultiplier: Int?,
        val minExtraSourceLines: Int?,
        val sourceLinesMultiplier: Double?,
        val seed: Int?,
        val maxComplexityMultiplier: Int?,
        val maxLineCountMultiplier: Int?
    ) {
        companion object {
            const val DEFAULT_SOLUTION_THROWS = false
            const val DEFAULT_MIN_TEST_COUNT = 64
            const val DEFAULT_MAX_TEST_COUNT = 1024
            const val DEFAULT_MIN_TIMEOUT = 128
            const val DEFAULT_MAX_TIMEOUT = 2048
            const val DEFAULT_TIMEOUT_MULTIPLIER = 32
            const val DEFAULT_MIN_MUTATION_COUNT = 0
            const val DEFAULT_OUTPUT_MULTIPLIER = 8
            const val DEFAULT_MAX_EXTRA_COMPLEXITY = 2
            const val DEFAULT_MAX_DEAD_CODE = 0
            const val DEFAULT_MAX_EXECUTION_COUNT_MULTIPLIER = 256L
            const val DEFAULT_EXECUTION_COUNT_FAILURE_MULTIPLIER = 4
            const val DEFAULT_EXECUTION_COUNT_TIMEOUT_MULTIPLIER = 16
            const val DEFAULT_ALLOCATION_FAILURE_MULTIPLIER = 4
            const val DEFAULT_ALLOCATION_LIMIT_MULTIPLIER = 16
            const val DEFAULT_MIN_EXTRA_SOURCE_LINES = 2
            const val DEFAULT_SOURCE_LINES_MULTIPLIER = 1.5
            const val DEFAULT_SEED = -1
            const val DEFAULT_MAX_COMPLEXITY_MULTIPLIER = 8
            const val DEFAULT_MAX_LINECOUNT_MULTIPLIER = 8
            const val DEFAULT_MIN_FAIL_FAST_COMPLEXITY = 16

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
                DEFAULT_MAX_LINECOUNT_MULTIPLIER
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
        var solutionExecutionCount: LanguagesResourceUsage? = null,
        var solutionAllocation: LanguagesResourceUsage? = null,
        var solutionDeadCode: LanguagesResourceUsage? = null,
        val checkBlacklist: Boolean = true,
        val disableLineCountLimit: Boolean = false,
        val disableAllocationLimit: Boolean = false,
        var solutionRecursiveMethods: LanguagesRecursiveMethods? = null,
        val minTestCount: Int = -1,
        val maxTestCount: Int = -1
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
        val solutionRecursiveMethods: LanguagesRecursiveMethods
    )

    @JsonClass(generateAdapter = true)
    data class LanguagesResourceUsage(val java: Long, val kotlin: Long? = null)

    @JsonClass(generateAdapter = true)
    data class LanguagesRecursiveMethods(
        val java: Set<ResourceMonitoringResults.MethodInfo>,
        val kotlin: Set<ResourceMonitoringResults.MethodInfo>? = null
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
        val expectedDeadCount: Int? = null
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
        @Transient
        val mutation: MutatedSource? = null
    ) {
        @Suppress("SpellCheckingInspection")
        enum class Reason {
            DESIGN, COMPILE, TEST, CHECKSTYLE, TIMEOUT, DEADCODE, LINECOUNT, TOOLONG, MEMORYLIMIT, RECURSION, COMPLEXITY, FEATURES, TOOMUCHOUTPUT, MEMOIZATION
        }
    }

    @JsonClass(generateAdapter = true)
    data class ValidationMutation(
        val deltas: List<String>,
        val language: Language,
        val incorrectIndex: Int?,
        val mutation: Mutation.Type?,
        val testCount: Int
    ) {
        @Transient
        private var _contents: String? = null
        fun contents(question: Question): String {
            if (_contents != null) {
                return _contents!!
            }
            return question.correctByLanguage[language]!!.let {
                DiffUtils.patch(it.lines(), UnifiedDiffUtils.parseUnifiedDiff(deltas))
            }.joinToString("\n").also {
                _contents = it
            }
        }

        @Transient
        private var _compiled: TestTestingSource? = null
        suspend fun compiled(question: Question): TestTestingSource {
            if (_compiled != null) {
                return _compiled!!
            }
            val results = TestResults(language)
            val source = question.contentsToSource(contents(question), language, results)
            return when (language) {
                Language.java ->
                    question.compileSubmission(
                        source,
                        InvertingClassLoader(setOf(question.klass)),
                        results
                    )
                Language.kotlin ->
                    question.kompileSubmission(
                        source,
                        InvertingClassLoader(setOf(question.klass, "${question.klass}Kt")),
                        results
                    )
            }.let {
                TestTestingSource(contents(question), question.fixTestingMethods(it.classLoader))
            }.also {
                _compiled = it
            }
        }
    }

    @delegate:Transient
    val compiledCommon by lazy {
        if (common?.isNotEmpty() == true) {
            val commonSources = common.mapIndexed { index, contents -> "Common$index.java" to contents }.toMap()
            Source(commonSources).compile(CompilationArguments(isolatedClassLoader = true, parameters = true))
        } else {
            null
        }
    }

    @delegate:Transient
    val compiledSolution by lazy {
        Source(mapOf("${question.klass}.java" to question.contents)).let { questionSource ->
            if (compiledCommon == null) {
                questionSource.compile(CompilationArguments(isolatedClassLoader = true, parameters = true))
            } else {
                questionSource.compile(
                    CompilationArguments(
                        parentClassLoader = compiledCommon!!.classLoader,
                        parentFileManager = compiledCommon!!.fileManager,
                        parameters = true
                    )
                )
            }
        }
    }

    data class TestTestingSource(val contents: String, val classloader: ClassLoader)

    @delegate:Transient
    val compiledSolutionForTesting by lazy {
        Source(mapOf("${question.klass}.java" to question.contents)).let { questionSource ->
            if (compiledCommon == null) {
                questionSource.compile(CompilationArguments(isolatedClassLoader = true, parameters = true))
            } else {
                questionSource.compile(
                    CompilationArguments(
                        parentClassLoader = compiledCommon!!.classLoader,
                        parentFileManager = compiledCommon!!.fileManager,
                        parameters = true
                    )
                )
            }
        }.let {
            TestTestingSource(question.contents, fixTestingMethods(it.classLoader))
        }
    }

    @delegate:Transient
    val compilationDefinedClass by lazy {
        compiledSolution.classLoader.definedClasses.topLevelClasses().let {
            require(it.size == 1)
            it.first()
        }.also {
            require(it == klass) {
                "Solution defined a name that is different from the parsed name: $it != $klass"
            }
        }
    }

    @delegate:Transient
    val solution by lazy {
        jenisol(compiledSolution.classLoader.loadClass(klass))
    }

    @delegate:Transient
    val featureChecker by lazy {
        compiledSolution.classLoader.loadClass(klass).declaredMethods.filter { it.isCheckFeatures() }.let {
            require(it.size <= 1) { "Can only use @CheckFeatures once" }
            it.firstOrNull()
        }?.also {
            CheckFeatures.validate(it)
        }
    }

    fun getTemplate(language: Language) = when (language) {
        Language.java -> javaTemplate
        Language.kotlin -> kotlinTemplate
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
    var validationResults: ValidationResults? = null

    val validated: Boolean
        get() = testingSettings != null

    var fauxStatic: Boolean = false

    var validationMutations: List<ValidationMutation>? = null
    suspend fun compileAllValidationMutations() = validationMutations!!.forEach { validationMutation ->
        validationMutation.compiled(this)
    }

    fun filename(language: Language) = "$klass.${language.extension()}"

    @Suppress("unused")
    companion object {
        const val DEFAULT_RETURN_TIMEOUT = 16
        const val MAX_START_MULTIPLE_COUNT = 128
        const val UNLIMITED_OUTPUT_LINES = 102400
        const val MIN_PER_TEST_LINES = 1024
        const val DEFAULT_MAX_OUTPUT_SIZE = 8 * 1024 * 1024

        @Transient
        val SAFE_PERMISSIONS =
            setOf(
                RuntimePermission("accessDeclaredMembers"),
                ReflectPermission("suppressAccessChecks"),
                RuntimePermission("getClassLoader"),
                RuntimePermission("localeServiceProvider")
            )
    }
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

fun Question.validationFile(sourceDir: String) = File(
    sourceDir,
    "${metadata.packageName.replace(".", File.separator)}/.validation.json"
)

fun Question.reportFile(sourceDir: String) = File(
    sourceDir,
    "${metadata.packageName.replace(".", File.separator)}/report.html"
)

fun loadFromPath(questionsFile: File, sourceDir: String, validated: Boolean = true): Map<String, Question> {
    return moshi.adapter<Map<String, Question>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Question::class.java
        )
    ).fromJson(questionsFile.readText())!!.toMutableMap().mapValues { (_, question) ->
        if (validated) {
            val validationPath = question.validationFile(sourceDir)
            if (!validationPath.exists()) {
                return@mapValues question
            }
            val validatedQuestion = try {
                moshi.adapter(Question::class.java).fromJson(validationPath.readText())!!
            } catch (e: Exception) {
                println("WARN: Validation file ${validationPath.path} does not match schema. Removing to be safe.")
                validationPath.delete()
                return@mapValues question
            }
            if (question.metadata.contentHash != validatedQuestion.metadata.contentHash) {
                return@mapValues question
            }
            validatedQuestion
        } else {
            question
        }
    }.toMap()
}

fun Collection<Question>.toJSON(): String =
    moshi.adapter<List<Question>>(Types.newParameterizedType(List::class.java, Question::class.java))
        .toJson(this.toList())

fun File.loadQuestions() = try {
    moshi.adapter<Map<String, Question>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Question::class.java
        )
    ).fromJson(readText())!!
} catch (e: Exception) {
    mapOf()
}

fun File.saveQuestions(questions: Map<String, Question>) =
    writeText(
        moshi.adapter<Map<String, Question>>(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Question::class.java
            )
        ).indent("  ").toJson(questions)
    )

@Suppress("SpellCheckingInspection")
fun String.toReason() = when (uppercase()) {
    "DESIGN" -> Question.IncorrectFile.Reason.DESIGN
    "TEST" -> Question.IncorrectFile.Reason.TEST
    "COMPILE" -> Question.IncorrectFile.Reason.COMPILE
    "CHECKSTYLE" -> Question.IncorrectFile.Reason.CHECKSTYLE
    "TIMEOUT" -> Question.IncorrectFile.Reason.TIMEOUT
    "DEADCODE" -> Question.IncorrectFile.Reason.DEADCODE
    "LINECOUNT" -> Question.IncorrectFile.Reason.LINECOUNT
    "TOOLONG" -> Question.IncorrectFile.Reason.TOOLONG
    "MEMORYLIMIT" -> Question.IncorrectFile.Reason.MEMORYLIMIT
    "RECURSION" -> Question.IncorrectFile.Reason.RECURSION
    "COMPLEXITY" -> Question.IncorrectFile.Reason.COMPLEXITY
    "FEATURES" -> Question.IncorrectFile.Reason.FEATURES
    "TOOMUCHOUTPUT" -> Question.IncorrectFile.Reason.TOOMUCHOUTPUT
    "MEMOIZATION" -> Question.IncorrectFile.Reason.MEMOIZATION
    else -> error("Invalid incorrect reason: $this")
}

private inline infix fun <reified T : Any> T.merge(other: T): T {
    val nameToProperty = T::class.declaredMemberProperties.associateBy { it.name }
    val primaryConstructor = T::class.primaryConstructor!!
    val args = primaryConstructor.parameters.associateWith { parameter ->
        val property = nameToProperty[parameter.name]!!

        (property.get(other) ?: property.get(this))
    }
    return primaryConstructor.callBy(args)
}
