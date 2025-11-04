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
        listOf("TestAllQuestions", "TestUnvalidatedQuestions", "TestFocusedQuestions")
            .map { testName -> project.layout.buildDirectory.file("questioner/$testName.kt").get().asFile }

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
                val questionsForFile = when (file.name) {
                    "TestAllQuestions.kt" -> questions
                    "TestUnvalidatedQuestions.kt" -> questions.filter { !it.validated }
                    "TestFocusedQuestions.kt" -> questions.filter { it.metadata?.focused == true }
                    else -> error("Invalid file name ${file.name}")
                }
                val klass = file.name.removeSuffix(".kt")
                val contents = if (questionsForFile.isNotEmpty()) {
                    questionsForFile.joinToString("\n") { question ->
                        question.generateSpec(project.rootProject.projectDir.toPath())
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
                        |class $klass : StringSpec() {
                        |  override suspend fun beforeSpec(spec: Spec) {
                        |    warm()
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
                file.writeText(contents.wrapForFile().ktFormat(KtLintArguments(indent = 2)))
            }
    }
}

fun Question.generateSpec(
    rootDirectory: Path,
): String {
    val correctPath = correctPath
    check(correctPath != null)
    val jsonPath = rootDirectory.resolve(Path.of(correctPath)).parent.resolve(".question.json")
    check(jsonPath.exists())
    return """
        |${"\"\"\""}${published.name} (${published.packageName}) should validate${"\"\"\""} {
        |    ${"\"\"\""}$jsonPath${"\"\"\""}.validate(options)
        |}
    """.trimMargin()
}

fun String.wrapForFile(): String = """
        |import edu.illinois.cs.cs125.questioner.lib.validate
        |import edu.illinois.cs.cs125.questioner.lib.ValidatorOptions
        |import edu.illinois.cs.cs125.questioner.lib.warm
        |import io.kotest.core.spec.style.StringSpec
        |import io.kotest.core.spec.Spec
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
