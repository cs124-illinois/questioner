@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

open class PrintSlowQuestions : DefaultTask() {
    init {
        group = "Verification"
        description = "Print slow questions."
    }

    @InputFile
    val inputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @TaskAction
    fun print() {
        val questions = inputFile.loadQuestionList()
        questions
            .filter { it.validated }
            .filter { it.validationResults!!.requiredTestCount > it.control.minTestCount!! }
            .sortedBy { -1 * it.validationResults!!.requiredTestCount }
            .forEach {
                project.logger.warn("${it.published.path}: ${it.validationResults!!.requiredTestCount} ${it.question.path}")
            }
    }
}
