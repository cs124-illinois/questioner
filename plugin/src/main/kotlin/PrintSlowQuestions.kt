@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class PrintSlowQuestions : DefaultTask() {
    init {
        group = "Verification"
        description = "Print slow questions."
    }

    @TaskAction
    fun print() {
        TODO()
        /*
        val questions =
            loadCoordinatesFromPath(
                project.layout.buildDirectory.dir("questioner/questions.json").get().asFile,
                project.javaSourceDir().path,
            )
        questions
            .filter { it.validated }
            .filter { it.published.validationResults!!.requiredTestCount > it.control.minTestCount!! }
            .sortedBy { -1 * it.published.validationResults!!.requiredTestCount }
            .forEach {
                println("${it.published.path}: ${it.validationResults!!.requiredTestCount} ${it.question.path}")
            }
         */
    }
}
