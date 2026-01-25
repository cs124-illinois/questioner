package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.ktFormat
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.dotenv
import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import edu.illinois.cs.cs125.questioner.lib.verifiers.toBase64
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

// Supported test file types for two-phase validation
enum class TestType {
    VALIDATE, // Phase 1 only (bootstrap, mutation, incorrect testing) - runs with JIT
    CALIBRATE, // Phase 2 only (calibration) - runs without JIT for consistent memory measurements
}

@Suppress("unused")
abstract class GenerateQuestionTests : DefaultTask() {
    @get:Input
    abstract var maxMutationCount: Int

    @get:Input
    abstract var retries: Int

    @get:Input
    abstract var verbose: Boolean

    @get:Input
    abstract var shuffleTests: Boolean

    @InputFile
    val inputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @OutputFiles
    val outputs =
        listOf(
            "TestValidateQuestions",
            "TestCalibrateQuestions",
            "TestValidateFocusedQuestions",
            "TestCalibrateFocusedQuestions",
        ).map { testName -> project.layout.buildDirectory.file("questioner/$testName.kt").get().asFile }

    @TaskAction
    fun generate() = runBlocking {
        val concurrency = dotenv.get("QUESTIONER_VALIDATION_CONCURRENCY")?.toInt() ?: 1

        val questions = inputFile.loadQuestionList().sortedBy { it.published.name }.let { questionList ->
            if (shuffleTests) {
                questionList.shuffled()
            } else {
                questionList
            }
        }

        outputs
            .filter { file ->
                file.name.startsWith("Test")
            }.forEach { file ->
                val (testType, questionsForFile) = when (file.name) {
                    "TestValidateQuestions.kt" -> TestType.VALIDATE to questions.filter { !it.phase1Completed }
                    "TestCalibrateQuestions.kt" -> TestType.CALIBRATE to questions.filter { it.phase1Completed && !it.validated }
                    "TestValidateFocusedQuestions.kt" -> TestType.VALIDATE to questions.filter { it.metadata?.focused == true && !it.phase1Completed }
                    "TestCalibrateFocusedQuestions.kt" -> TestType.CALIBRATE to questions.filter { it.metadata?.focused == true && it.phase1Completed && !it.validated }
                    else -> error("Invalid file name ${file.name}")
                }
                val klass = file.name.removeSuffix(".kt")
                val contents = if (questionsForFile.isNotEmpty()) {
                    questionsForFile.joinToString("\n") { question ->
                        question.generateSpec(project.rootProject.projectDir.toPath(), testType)
                    }
                } else {
                    when (file.name) {
                        "TestValidateQuestions.kt" -> "no questions need phase 1 validation"
                        "TestCalibrateQuestions.kt" -> "no questions need phase 2 calibration"
                        "TestValidateFocusedQuestions.kt" -> "no focused questions need phase 1 validation"
                        "TestCalibrateFocusedQuestions.kt" -> "no focused questions need phase 2 calibration"
                        else -> error("Invalid file name ${file.name}")
                    }.let { message ->
                        """"$message" {}"""
                    }
                }.let { inner ->
                    """
                        |@OptIn(io.kotest.common.ExperimentalKotest::class)
                        |class $klass : StringSpec() {
                        |  override suspend fun beforeSpec(spec: Spec) {
                        |    warm()
                        |    JeedCacheStats.reset()
                        |  }
                        |  override suspend fun afterSpec(spec: Spec) {
                        |    val caffeineStats = compilationCache.stats()
                        |    val diskSizeMB = JeedCacheStats.diskCacheSizeBytes / 1024.0 / 1024.0
                        |    println("Jeed Cache Stats for $klass:")
                        |    println("  L1 (memory) hits: ${'$'}{JeedCacheStats.l1Hits}")
                        |    println("  L2 (disk) hits: ${'$'}{JeedCacheStats.l2Hits}")
                        |    println("  Total hits: ${'$'}{JeedCacheStats.totalHits}")
                        |    println("  Misses: ${'$'}{JeedCacheStats.misses}")
                        |    println("  Hit rate: ${'$'}{"%.2f".format(if (JeedCacheStats.totalHits + JeedCacheStats.misses > 0) JeedCacheStats.totalHits * 100.0 / (JeedCacheStats.totalHits + JeedCacheStats.misses) else 0.0)}%")
                        |    println("  Disk cache in use: ${'$'}{useDiskCache}")
                        |    println("  Disk cache size: ${'$'}{"%.2f".format(diskSizeMB)} MB")
                        |    println("  Caffeine stats: hits=${'$'}{caffeineStats.hitCount()}, misses=${'$'}{caffeineStats.missCount()}, hitRate=${'$'}{"%.2f".format(caffeineStats.hitRate() * 100)}%")
                        |  }
                        |  init {${
                        if (questionsForFile.isNotEmpty()) {
                            """
                        |    val options = ValidatorOptions($maxMutationCount, $retries, $verbose, "${project.rootProject.projectDir.toPath().pathString.toBase64()}")
                        |    concurrency = $concurrency
                        |    threads = $concurrency
                        |    """
                        } else {
                            ""
                        }
                    }${inner.lines().joinToString("\n") { "    $it" }}
                    |  }
                    |}
                    """.trimMargin()
                }
                file.writeText(contents.wrapForFile(testType).ktFormat(KtLintArguments(indent = 2)))
            }
    }
}

fun Question.generateSpec(
    rootDirectory: Path,
    testType: TestType,
): String {
    // Compute the hash from author/slug to find the question JSON file
    val fullSlug = "${published.author}/${published.path}"
    val hash = fullSlug.sha256Take16()
    val jsonPath = rootDirectory.resolve("build/questioner/questions/$hash.question.json")
    check(jsonPath.exists()) { "Question JSON not found at $jsonPath for ${published.name}" }

    val (methodName, actionDescription) = when (testType) {
        TestType.VALIDATE -> "validate" to "should complete validation"
        TestType.CALIBRATE -> "calibrate" to "should complete calibration"
    }

    return """
        |${"\"\"\""}${published.name} (${published.packageName}) $actionDescription${"\"\"\""} {
        |    ${"\"\"\""}$jsonPath${"\"\"\""}.$methodName(options)
        |}
    """.trimMargin()
}

fun String.wrapForFile(testType: TestType): String {
    val imports = when (testType) {
        TestType.VALIDATE -> "import edu.illinois.cs.cs125.questioner.lib.validate"
        TestType.CALIBRATE -> "import edu.illinois.cs.cs125.questioner.lib.calibrate"
    }
    return """
        |$imports
        |import edu.illinois.cs.cs125.questioner.lib.ValidatorOptions
        |import edu.illinois.cs.cs125.questioner.lib.warm
        |import io.kotest.core.spec.style.StringSpec
        |import io.kotest.core.spec.Spec
        |import edu.illinois.cs.cs125.jeed.core.compilationCache
        |import edu.illinois.cs.cs125.jeed.core.JeedCacheStats
        |import edu.illinois.cs.cs125.jeed.core.useDiskCache
        |
        |/*
        | * THIS FILE IS AUTOGENERATED. DO NOT EDIT BY HAND.
        | */
        |
        |${trim()}
        |
        |/*
        | * THIS FILE IS AUTOGENERATED. DO NOT EDIT BY HAND.
        | */
    """.trimMargin()
}
