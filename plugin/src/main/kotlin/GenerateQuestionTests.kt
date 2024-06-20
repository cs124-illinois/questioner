package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.ktFormat
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import io.kotest.common.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

const val GENERATION_SEED = 124

@Suppress("unused")
abstract class GenerateQuestionTests : DefaultTask() {
    @get:Input
    abstract var maxMutationCount: Int

    @get:Input
    abstract var concurrency: Int

    @get:Input
    abstract var retries: Int

    @get:Input
    abstract var quiet: Boolean

    @get:Input
    abstract var shuffleTests: Boolean

    @get:Input
    abstract var timeoutAdjustment: Double

    @InputFile
    val inputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @OutputFiles
    val outputs =
        listOf("TestAllQuestions", "TestUnvalidatedQuestions", "TestFocusedQuestions")
            .mapNotNull { testName -> project.layout.buildDirectory.file("questioner/$testName.kt").get().asFile }

    @TaskAction
    fun generate() = runBlocking {
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
                val questionsForFile = when (file.name) {
                    "TestAllQuestions.kt" -> questions
                    "TestUnvalidatedQuestions.kt" -> questions.filter { !it.validated }
                    "TestFocusedQuestions.kt" -> questions.filter { it.metadata?.focused == true }
                    else -> error("Invalid file name ${file.name}")
                }
                val klass = file.name.removeSuffix(".kt")
                val contents = if (questionsForFile.isNotEmpty()) {
                    questionsForFile.joinToString("\n") { question ->
                        question.generateSpec(
                            GENERATION_SEED,
                            maxMutationCount,
                            retries,
                            quiet,
                            timeoutAdjustment,
                        )
                    }
                } else {
                    when (file.name) {
                        "TestAllQuestions.kt" -> "no questions found"
                        "TestUnvalidatedQuestions.kt" -> "no unvalidated questions found"
                        "TestFocusedQuestions.kt" -> "no focused questions"
                        else -> error("Invalid file name ${file.name}")
                    }.let { message ->
                        """"$message" {}"""
                    }
                }.let { inner ->
                    """
                        |@OptIn(io.kotest.common.ExperimentalKotest::class)
                        |class $klass : StringSpec({
                        |${
                        if (questionsForFile.isNotEmpty()) {
                            """    concurrency = $concurrency
                        |    threads = $concurrency
                        |    """
                        } else {
                            ""
                        }
                    }${inner.lines().joinToString("\n") { "    $it" }}
                    })
                    """.trimMargin()
                }
                file.writeText(
                    contents.wrapForFile(questionsForFile.isNotEmpty()).ktFormat(KtLintArguments(indent = 4)),
                )
            }
    }
}

fun Question.generateSpec(
    seed: Int,
    maxMutationCount: Int,
    retries: Int,
    quiet: Boolean,
    timeoutAdjustment: Double,
): String {
    val correctPath = correctPath
    check(correctPath != null)
    val jsonPath = Path.of(correctPath).parent.resolve(".question.json")
    check(jsonPath.exists())
    return """
        |${"\"\"\""}${published.name} (${published.packageName}) should validate${"\"\"\""} {
        |    ${"\"\"\""}$jsonPath${"\"\"\""}.validate(seed = $seed, maxMutationCount = $maxMutationCount, retries = $retries, quiet = $quiet, timeoutAdjustment = $timeoutAdjustment)
        |}
    """.trimMargin()
}

fun String.wrapForFile(isNotEmpty: Boolean): String {
    return """
        |import edu.illinois.cs.cs125.questioner.lib.validate
        |import io.kotest.core.spec.style.StringSpec
        |${
        if (isNotEmpty) {
            """
            |import kotlinx.coroutines.runBlocking
            |import edu.illinois.cs.cs125.jeed.core.warm
            |
            """.trimMargin()
        } else {
            ""
        }
    }
        |/*
        | * THIS FILE IS AUTOGENERATED. DO NOT EDIT BY HAND.
        | */
        | 
        |${this.trim()}
        |
        |/*
        | * THIS FILE IS AUTOGENERATED. DO NOT EDIT BY HAND.
        | */
    """.trimMargin()
}
