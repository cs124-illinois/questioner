@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import edu.illinois.cs.cs125.questioner.lib.toJSON
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.GZIPOutputStream

abstract class PublishQuestions : DefaultTask() {
    @Input
    lateinit var token: String

    @Input
    lateinit var destination: String

    @InputFile
    val inputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @get:Input
    abstract var ignorePackages: List<String>

    init {
        group = "Publish"
        description = "Publish questions."
    }

    @TaskAction
    fun publish() {
        val uri = URI(destination)
        require(uri.scheme == "http" || uri.scheme == "https") { "Invalid destination scheme: ${uri.scheme}" }

        val allQuestions = inputFile.loadQuestionList()
        val questions = allQuestions.filter { question ->
            question.metadata?.publish != false && !ignorePackages.any { prefix ->
                question.published.packageName.startsWith(prefix)
            }
        }
        require(questions.isNotEmpty()) {
            val ignoredQuestions = allQuestions.filter { question -> question.metadata?.publish == false }
            "No questions to publish: ${
                if (ignorePackages.isNotEmpty()) {
                    "disabled packages ${ignorePackages.joinToString(",")}"
                } else {
                    ""
                }
            }${
                if (ignoredQuestions.isNotEmpty()) {
                    ", ignored questions ${
                        ignoredQuestions.map { question -> question.published.packageName }.joinToString(",")
                    }"
                } else {
                    ""
                }
            }"
        }
        require(questions.all { question -> question.validated }) { "Cannot publish until all questions are validated" }
        questions.forEach { question -> question.cleanForUpload() }

        HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", """Bearer $token""")
            .header("Content-Type", "application/json")
            .header("Content-Encoding", "gzip")
            .POST(HttpRequest.BodyPublishers.ofByteArray(questions.toJSON().gzip()))
            .build().let { httpRequest ->
                HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString())
            }.also { httpResponse ->
                check(httpResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                    val message = try {
                        httpResponse.body()
                    } catch (e: Exception) {
                        null
                    }
                    "Upload request to $destination failed: ${httpResponse.statusCode()}${
                        if (message != null) {
                            "\n$message"
                        } else {
                            ""
                        }
                    }"
                }
            }
    }
}

private fun String.gzip(): ByteArray? {
    check(this.isNotEmpty())
    val obj = ByteArrayOutputStream()
    GZIPOutputStream(obj).apply {
        write(toByteArray())
        close()
    }
    return obj.toByteArray()
}
