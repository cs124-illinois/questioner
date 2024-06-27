@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.net.URI

open class CheckQuestionerVersion : DefaultTask() {
    init {
        group = "Verification"
        description = "Check questioner version."
    }

    @TaskAction
    fun print() = runBlocking {
        try {
            URI("http://www.google.com").toURL().openConnection().let {
                it.connect()
                it.getInputStream().close()
            }
        } catch (e: Exception) {
            error("Unable to retrieve latest Questioner version: network not connected.")
        }
        if (!isLatestVersion()) {
            val latestVersion = getLatestQuestionerVersion()
            project.logger.warn("\nPlease upgrade your build.gradle.kts to use the latest version of Questioner: $latestVersion\n")
        }
    }
}
