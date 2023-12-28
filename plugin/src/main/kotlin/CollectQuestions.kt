package edu.illinois.cs.cs125.questioner.plugin

import com.squareup.moshi.Types
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.loadQuestion
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import edu.illinois.cs.cs125.questioner.plugin.parse.ParsedJavaFile
import io.kotest.inspectors.forAll
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

abstract class CollectQuestions : DefaultTask() {
    @OutputFile
    val outputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @InputFiles
    @Suppress("unused")
    val inputFiles: FileCollection = project.extensions.getByType(JavaPluginExtension::class.java)
        .sourceSets.getByName("main")
        .allSource.filter { file -> file.name == ".question.json" }

    @TaskAction
    fun collect() {
        val questions = inputFiles.files
            .map { file ->
                val question = file.loadQuestion()!!
                val correctPath = Path.of(file.path).parent.resolve("${question.klass}.java")
                if (!correctPath.exists() || !ParsedJavaFile(correctPath.toFile()).isCorrect) {
                    logger.warn("Removing question file $file that matches no @Correct annotation")
                    file.delete()
                    return@map null
                }
                question.apply {
                    this.correctPath = correctPath.toString()
                }
            }.filterNotNull()
            .sortedBy { it.published.name }

        check(questions.map { q -> q.published.contentHash }.toSet().size == questions.size) {
            "Duplicate Question hash"
        }

        questions
            .filter { q -> q.published.descriptions[Language.java] != null }
            .groupBy { q -> q.published.descriptions[Language.java] }
            .filterValues { it.size > 1 }
            .forAll { (_, questions) ->
                logger.warn("Found questions with identical Java descriptions: ${questions.joinToString { q -> q.published.name }}")
            }

        questions
            .filter { q -> q.published.descriptions[Language.kotlin] != null }
            .groupBy { q -> q.published.descriptions[Language.kotlin] }
            .filterValues { it.size > 1 }
            .forAll { (_, questions) ->
                logger.warn("Found questions with identical Kotlin descriptions: ${questions.joinToString { q -> q.published.name }}")
            }

        questions.map { q -> q.metadata.unusedFiles }.flatten().forEach {
            logger.warn("$it will not be included in the build")
        }

        questions.writeToFile(outputFile)
    }
}

private fun Collection<Question>.writeToFile(file: File) {
    file.writeText(
        moshi.adapter<List<Question>>(
            Types.newParameterizedType(List::class.java, Question::class.java),
        ).indent("  ").toJson(this.toList()),
    )
}