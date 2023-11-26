@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.loadQuestionsFromPath
import edu.illinois.cs.cs125.questioner.lib.toJSON
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.com.google.api.client.http.HttpStatusCodes
import java.net.URI

open class PublishQuestions : DefaultTask() {
    @Input
    lateinit var token: String

    @Input
    lateinit var destination: String

    init {
        group = "Build"
        description = "Publish questions."
    }

    @TaskAction
    fun publish() {
        val uri = URI(destination)
        require(uri.scheme == "http" || uri.scheme == "https") { "Invalid destination scheme: ${uri.scheme}" }
        val questions =
            loadQuestionsFromPath(
                project.layout.buildDirectory.dir("questioner/questions.json").get().asFile,
                project.javaSourceDir().path,
            ).filterKeys {
                it.metadata.publish != false
            }

        require(questions.isNotEmpty()) { "No questions to publish" }
        require(questions.keys.all { it.validated }) { "Cannot publish until all questions are validated" }
        Request
            .post(uri)
            .addHeader("authorization", """Bearer $token""")
            .bodyString(questions.values.toJSON(), ContentType.APPLICATION_JSON)
            .execute()
            .returnResponse().also {
                check(it.code == HttpStatusCodes.STATUS_CODE_OK) { "Bad status for $destination: ${it.code}" }
            }
    }
}
