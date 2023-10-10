@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.loadFromPath
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class PrintSlowQuestions : DefaultTask() {
    init {
        group = "Verification"
        description = "Print slow questions."
    }

    @TaskAction
    fun print() {
        val questions =
            loadFromPath(
                project.layout.buildDirectory.dir("questioner/questions.json").get().asFile,
                project.javaSourceDir().path,
            ).values
        questions
            .filter { it.validated }
            .filter { it.validationResults!!.requiredTestCount > it.control.minTestCount!! }
            .sortedBy { -1 * it.validationResults!!.requiredTestCount }
            .forEach {
                println("${it.published.path}: ${it.validationResults!!.requiredTestCount} ${it.question.path}")
            }
    }
}
