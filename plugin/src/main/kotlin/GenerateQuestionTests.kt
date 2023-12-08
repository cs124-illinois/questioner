package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.ktFormat
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.QuestionCoordinates
import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import io.kotest.common.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

fun Project.javaSourceDir(): File =
    extensions.getByType(JavaPluginExtension::class.java)
        .sourceSets.getByName("main").java.srcDirs.let {
            check(it.size == 1) { "Found multiple source directories: ${it.joinToString(",")}" }
            it.first()!!
        }

@Suppress("unused")
abstract class GenerateQuestionTests : DefaultTask() {
    @get:Input
    abstract var seed: Int

    @get:Input
    abstract var maxMutationCount: Int

    @get:Input
    abstract var concurrency: Int

    @get:Input
    abstract var retries: Int

    @InputFile
    val inputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @OutputFiles
    val outputs =
        listOf("TestAllQuestions", "TestUnvalidatedQuestions", "TestFocusedQuestions")
            .mapNotNull { testName -> project.layout.buildDirectory.file("questioner/$testName.kt").get().asFile }

    @TaskAction
    fun generate() = runBlocking {
        val questions = inputFile.loadQuestionList().sortedBy { it.published.name }

        outputs
            .filter { file ->
                file.name.startsWith("Test")
            }.forEach { file ->
                val questionsForFile = when (file.name) {
                    "TestAllQuestions.kt" -> questions
                    "TestUnvalidatedQuestions.kt" -> questions.filter { !it.validated }
                    "TestFocusedQuestions.kt" -> questions.filter { it.metadata.focused == true }
                    else -> error("Invalid file name ${file.name}")
                }
                val klass = file.name.removeSuffix(".kt")
                val contents = if (questionsForFile.isNotEmpty()) {
                    questionsForFile.joinToString("\n") { question -> question.generateSpec(seed, maxMutationCount, retries) }
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
                        |    beforeSpec {
                        |        runBlocking {
                        |            warm(failLint = false, quiet = true, useDocker = false)
                        |        }
                        |    }
                        |    """
                        } else {
                            ""
                        }}${ inner.lines().joinToString("\n") { "    $it" } }
                    })
                    """.trimMargin()
                }
                file.writeText(contents.wrapForFile(questionsForFile.isNotEmpty()).ktFormat(KtLintArguments(indent = 4)))
            }

        /*
        outputs.find { file -> file.name == "Config.kt" }!!.writeText(
            """
            |import io.kotest.core.config.AbstractProjectConfig
            |
            |object KotestProjectConfig : AbstractProjectConfig() {
            |    override val parallelism = 4
            |}
            """.trimMargin(),
        )
         */
        /*
            val input = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

            val sourceRoot = project.javaSourceDir()
            val tests = loadCoordinatesFromPath(input, sourceRoot.path).organizeTests()
            if (tests.isEmpty()) {
                logger.warn("No questions found.")
                return
            }

            val testRoot = project.file("src/test/kotlin")
            if (testRoot.exists()) {
                require(testRoot.isDirectory) { "test generation destination must be a directory" }
            } else {
                testRoot.mkdirs()
            }

            tests.first().let { (packageName, questions) ->
                project.file("src/test/kotlin/TestAllQuestions.kt").also {
                    it.parentFile.mkdirs()
                    it.writeText(
                        questions.generateTest(
                            packageName,
                            "TestAllQuestions",
                            sourceRoot,
                            seed,
                            maxMutationCount,
                        ),
                    )
                }
                project.file("src/test/kotlin/TestUnvalidatedQuestions.kt").also {
                    it.parentFile.mkdirs()
                    it.writeText(
                        questions.generateTest(
                            packageName,
                            "TestUnvalidatedQuestions",
                            sourceRoot,
                            seed,
                            maxMutationCount,
                            onlyNotValidated = true,
                        ),
                    )
                }
                project.file("src/test/kotlin/TestFocusedQuestions.kt").also {
                    it.parentFile.mkdirs()
                    it.writeText(
                        questions.generateTest(
                            packageName,
                            "TestFocusedQuestions",
                            sourceRoot,
                            seed,
                            maxMutationCount,
                            onlyFocused = true,
                        ),
                    )
                }
            }
         */
    }
}

fun List<Question>.sortForTesting() = sortedWith(
    compareBy(
        { question ->
            question.metadata.packageName.split(".").size
        },
        { question ->
            question.metadata.packageName.split(".").last()
        },
    ),
)

fun List<Question>.organizeTests() =
    map { it.metadata.packageName.packageNames() }
        .flatten()
        .distinct()
        .sortedBy { it.split(".").size }
        .let { packageNames ->
            val byPackage: MutableMap<String, List<Question>> = mutableMapOf()
            packageNames.forEach { name ->
                val depth = name.split(".").size
                check(depth >= 1) { "Invalid depth when organizing questions" }
                val previous =
                    if (depth == 1) {
                        null
                    } else {
                        name.split(".").subList(0, depth - 1).joinToString(".")
                    }
                val packageQuestions = filter { it.metadata.packageName.startsWith(name) }
                if (previous != null && byPackage[previous]?.size == packageQuestions.size) {
                    byPackage.remove(previous)
                }
                byPackage[name] = packageQuestions
            }
            byPackage
        }.entries
        .sortedBy { it.key.length }
        .map { it.value }
        .flatten()

fun Question.generateSpec(seed: Int, maxMutationCount: Int, retries: Int): String {
    assert(correctPath != null)
    val jsonPath = Path.of(correctPath).parent.resolve(".question.json")
    assert(jsonPath.exists())
    return """
        |"${published.name} (${published.packageName}) should validate" {
        |    "$jsonPath".validate(seed = $seed, maxMutationCount = $maxMutationCount, retries = $retries)
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

fun List<QuestionCoordinates>.generateTest(
    packageName: String,
    klass: String,
    sourceRoot: File,
    seed: Int,
    maxMutationCount: Int,
    onlyNotValidated: Boolean = false,
    onlyFocused: Boolean = false,
): String {
    var isBlank = false
    val testBlock =
        filter { it.metadata.packageName.startsWith(packageName) }
            .filter {
                when {
                    onlyNotValidated -> !it.validated
                    onlyFocused -> it.metadata.focused == true
                    else -> true
                }
            }
            .sortedBy { it.published.name }
            .joinToString(separator = "\n") {
                """|  "${it.published.name} (${it.metadata.packageName}) should validate" {
               |    validator.validate("${it.published.name}", verbose = false, force = ${
                    if (onlyFocused) {
                        "true"
                    } else {
                        "false"
                    }
                })
               |  }
                """.trimMargin()
            }.let {
                it.ifBlank {
                    isBlank = true
                    val description =
                        when {
                            onlyNotValidated -> "unvalidated "
                            onlyFocused -> "focused "
                            else -> ""
                        }
                    """  "no ${description}questions found" { }"""
                }
            }
    val packageNameBlock =
        if (packageName.isNotEmpty()) {
            "package $packageName\n\n"
        } else {
            ""
        }
    return """$packageNameBlock@file:Suppress("SpellCheckingInspection", "UnusedImport", "ktlint:standard:max-line-length", "unused")

${
        if (!isBlank) {
            """import edu.illinois.cs.cs125.jeed.core.warm
import edu.illinois.cs.cs125.questioner.lib.Validator
"""
        } else {
            ""
        }
    }import io.kotest.core.spec.style.StringSpec
${
        if (!isBlank) {
            """import kotlinx.coroutines.runBlocking
import java.nio.file.Path
"""
        } else {
            ""
        }
    }
/*
 * THIS FILE IS AUTOGENERATED. DO NOT EDIT BY HAND.
 */
${
        if (!isBlank) {
            """
private val validator = Validator(
  Path.of(object {}::class.java.getResource("/questions.json")!!.toURI()).toFile(),
  ${"\"\"\"" + sourceRoot.path.replace("/", System.getProperty("file.separator")) + "\"\"\""},
  seed = $seed,
  maxMutationCount = $maxMutationCount,
)
"""
        } else {
            ""
        }
    }
@Suppress("MaxLineLength", "LargeClass")
class $klass : StringSpec({${
        if (!isBlank) {
            """
  beforeSpec {
    runBlocking {
      warm(failLint = false, quiet = true, useDocker = false)
    }
  }"""
        } else {
            ""
        }
    }
$testBlock
})
// AUTOGENERATED"""
}

fun String.packageNames() =
    split(".").let {
        mutableSetOf<String>().also { all ->
            for (i in 0..it.size) {
                all.add(it.subList(0, i).joinToString("."))
            }
        }.toSet()
    }
