package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.QuestionFiles
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import kotlinx.serialization.encodeToString
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CollectQuestions : DefaultTask() {
    @OutputFile
    val outputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @InputFiles
    val inputFiles: FileCollection = project.fileTree(
        project.layout.buildDirectory.dir("questioner/questions"),
    ).matching { it.include("*.parsed.json") }

    @TaskAction
    fun collect() {
        val questions = inputFiles.files
            .mapNotNull { file ->
                // Merge parsed question with validation and calibration results
                val question = QuestionFiles.mergeQuestion(file.absolutePath)
                if (question == null) {
                    logger.warn("Could not load question from $file")
                    return@mapNotNull null
                }
                // correctPath is already set by SaveQuestion task
                question
            }
            .sortedBy { it.published.name }

        check(questions.map { q -> q.published.contentHash }.toSet().size == questions.size) {
            "Found duplicate question hash"
        }

        questions.map { q ->
            "${q.published.path}${
                if (q.published.author !== "") {
                    "/${q.published.author}"
                } else {
                    ""
                }
            }"
        }
            .groupingBy { path -> path }
            .eachCount()
            .filter { it.value > 1 }.let { duplicateList ->
                check(duplicateList.isEmpty()) {
                    "Found questions with duplicate coordinates: ${duplicateList.map { it.key }.joinToString(",")}"
                }
            }

        questions
            .filter { q -> q.published.descriptions[Language.java] != null }
            .groupBy { q -> q.published.descriptions[Language.java] }
            .filterValues { it.size > 1 }
            .forEach { (_, questions) ->
                logger.warn("Found questions with identical Java descriptions: ${questions.joinToString { q -> q.published.name }}")
            }

        questions
            .filter { q -> q.published.descriptions[Language.kotlin] != null }
            .groupBy { q -> q.published.descriptions[Language.kotlin] }
            .filterValues { it.size > 1 }
            .forEach { (_, questions) ->
                logger.warn("Found questions with identical Kotlin descriptions: ${questions.joinToString { q -> q.published.name }}")
            }

        questions.map { q -> q.metadata?.unusedFiles ?: listOf() }.flatten().forEach {
            logger.warn("$it will not be included in the build")
        }

        questions.writeToFile(outputFile)
    }
}

private fun Collection<Question>.writeToFile(file: File) {
    file.writeText(json.encodeToString(this.toList()))
}
