package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.util.function.BiPredicate

abstract class DumpQuestions : DefaultTask() {
    @Internal
    lateinit var endpoint: QuestionerConfig.EndPoint

    @InputFile
    val inputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @Internal
    lateinit var ignorePackages: List<String>

    @Internal
    lateinit var publishIncludes: BiPredicate<QuestionerConfig.EndPoint, Question>

    init {
        group = "Publish"
        description = "Dump question that would be published."
    }

    @TaskAction
    fun publish() {
        val uri = URI(endpoint.url)
        require(uri.scheme == "http" || uri.scheme == "https") { "Invalid destination scheme: ${uri.scheme}" }

        val allQuestions = inputFile.loadQuestionList().filter { question ->
            !ignorePackages.any { prefix -> question.published.packageName.startsWith(prefix) }
        }

        val questions = allQuestions.filter { question ->
            question.metadata?.publish != false
        }.filter { question ->
            publishIncludes.test(endpoint, question)
        }

        if (questions.isEmpty()) {
            val ignoredQuestions = allQuestions.filter { question -> question.metadata?.publish == false }
            val unpublishedQuestions = ignoredQuestions.filter { question -> !publishIncludes.test(endpoint, question) }
            println(
                "No questions would be published to endpoint ${endpoint.name}: ${
                    if (ignorePackages.isNotEmpty()) {
                        "disabled packages ${ignorePackages.joinToString(",")}"
                    } else {
                        ""
                    }
                }${
                    if (ignoredQuestions.isNotEmpty()) {
                        ", ignored questions ${
                            ignoredQuestions.joinToString(",") { question -> question.published.path }
                        }"
                    } else {
                        ""
                    }
                }${
                    if (unpublishedQuestions.isNotEmpty()) {
                        ", unpublished questions ${
                            unpublishedQuestions.joinToString(",") { question -> question.published.path }
                        }"
                    } else {
                        ""
                    }
                }",
            )
            return
        }
        val totalCount = questions.size
        val unvalidatedCount = questions.filter { question -> !question.validated }.size
        println("-".repeat(80))
        println("$totalCount questions would be published to ${endpoint.name} ($unvalidatedCount are currently unvalidated)")
        println("-".repeat(80))
        questions.forEach { question ->
            print("${question.published.path}/${question.published.author}/${question.published.version}")
            print(" (${question.published.languages.sorted().joinToString(",")})")
            println()
        }
        println("-".repeat(80))
        println("$totalCount questions would be published to ${endpoint.name} ($unvalidatedCount are currently unvalidated)")
        println("-".repeat(80))
    }
}
