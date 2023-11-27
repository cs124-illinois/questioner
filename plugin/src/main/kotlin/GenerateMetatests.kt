package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.QuestionCoordinates
import edu.illinois.cs.cs125.questioner.lib.loadCoordinatesFromPath
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

@Suppress("unused")
abstract class GenerateMetatests : DefaultTask() {
    init {
        group = "Build"
        description = "Generate question metatests from JSON."
    }

    @get:Input
    abstract var seed: Int

    @get:Input
    abstract var maxMutationCount: Int

    @InputFiles
    val inputFiles =
        project.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName("main").allSource.filter { it.name == ".validation.json" }
            .toMutableList() + project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @OutputFiles
    val outputs =
        listOf("TestAllQuestions", "TestUnvalidatedQuestions", "TestFocusedQuestions").map {
            project.file("src/test/kotlin/$it.kt")
        }

    @TaskAction
    fun generate() {
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
    }
}

fun Collection<QuestionCoordinates>.organizeTests() =
    map {
        it.metadata.packageName.packageNames()
    }.flatten().distinct().sortedBy { it.split(".").size }.let { packageNames ->
        val byPackage: MutableMap<String, List<QuestionCoordinates>> = mutableMapOf()
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
    }.entries.sortedBy { it.key.length }

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
${if (!isBlank) { """
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
