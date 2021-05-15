package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.loadFromPath
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
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

    @InputFiles
    val inputFiles = project.convention.getPlugin(JavaPluginConvention::class.java)
        .sourceSets.getByName("main").allSource.filter { it.name == ".validation.json" }
        .toMutableList() + File(project.buildDir, "questioner/questions.json")

    @OutputFiles
    val outputs = listOf("TestAllQuestions", "TestUnvalidatedQuestions", "TestFocusedQuestions").map {
        project.file("src/test/kotlin/${it}.kt")
    }

    @TaskAction
    fun generate() {
        val input = File(project.buildDir, "questioner/questions.json")

        val sourceRoot = project.javaSourceDir()
        val tests = loadFromPath(input, sourceRoot.path).values.organizeTests()
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
                it.writeText(questions.generateTest(packageName, "TestAllQuestions", sourceRoot, seed))
            }
            project.file("src/test/kotlin/TestUnvalidatedQuestions.kt").also {
                it.parentFile.mkdirs()
                it.writeText(
                    questions.generateTest(
                        packageName,
                        "TestUnvalidatedQuestions",
                        sourceRoot,
                        seed,
                        onlyNotValidated = true
                    )
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
                        onlyFocused = true
                    )
                )
            }
        }
    }
}

fun Collection<Question>.organizeTests() = map {
    it.metadata.packageName.packageNames()
}.flatten().distinct().sortedBy { it.split(".").size }.let { packageNames ->
    val byPackage: MutableMap<String, List<Question>> = mutableMapOf()
    packageNames.forEach { name ->
        val depth = name.split(".").size
        check(depth >= 1) { "Invalid depth when organizing questions" }
        val previous = if (depth == 1) {
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

fun List<Question>.generateTest(
    packageName: String,
    klass: String,
    sourceRoot: File,
    seed: Int,
    onlyNotValidated: Boolean = false,
    onlyFocused: Boolean = false
): String {
    val testBlock = filter { it.metadata.packageName.startsWith(packageName) }
        .filter {
            when {
                onlyNotValidated -> !it.validated
                onlyFocused -> it.metadata.focused
                else -> true
            }
        }
        .sortedBy { it.name }
        .joinToString(separator = "\n") {
            """  "${it.name} (${it.metadata.packageName}) should validate" {
    validator.validate("${it.name}", verbose = false, force = ${
                if (onlyFocused) {
                    "true"
                } else {
                    "false"
                }
            })
  }"""
        }.let {
            it.ifBlank {
                val description = when {
                    onlyNotValidated -> "unvalidated "
                    onlyFocused -> "focused "
                    else -> ""
                }
                """  "no ${description}questions found" { }"""
            }
        }
    val packageNameBlock = if (packageName.isNotEmpty()) {
        "package $packageName\n\n"
    } else {
        ""
    }
    return """$packageNameBlock@file:Suppress("SpellCheckingInspection", "UnusedImport", "unused")

import edu.illinois.cs.cs125.questioner.lib.Validator
import io.kotest.core.spec.style.StringSpec
import java.nio.file.Path

/*
 * THIS FILE IS AUTOGENERATED. DO NOT EDIT BY HAND.
 */

/* ktlint-disable max-line-length */

private val validator = Validator(
  Path.of(object {}::class.java.getResource("/questions.json")!!.toURI()).toFile(),
  "${sourceRoot.path}",
  seed = $seed
)
@Suppress("MaxLineLength", "LargeClass")
class $klass : StringSpec({
$testBlock
})

/* ktlint-enable max-line-length */
// AUTOGENERATED
"""
}

fun String.packageNames() = split(".").let {
    mutableSetOf<String>().also { all ->
        for (i in 0..it.size) {
            all.add(it.subList(0, i).joinToString("."))
        }
    }.toSet()
}
