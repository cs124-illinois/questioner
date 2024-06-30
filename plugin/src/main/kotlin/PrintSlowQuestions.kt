@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

open class PrintSlowQuestions : DefaultTask() {
    init {
        group = "Verification"
        description = "Print slow questions."
    }

    @InputFile
    val inputFile: File = project.layout.buildDirectory.dir("questioner/questions.json").get().asFile

    @Internal
    lateinit var ignorePackages: List<String>

    @TaskAction
    fun print() {
        inputFile.loadQuestionList()
            .filter { !ignorePackages.any { prefix -> it.published.packageName.startsWith(prefix) } }
            .filter { it.validated }
            .filter { it.validationResults!!.requiredTestCount > it.control.minTestCount!! }
            .sortedBy { -1 * it.validationResults!!.requiredTestCount }
            .forEach {
                val fullPath = project.rootProject.projectDir.resolve(it.correctPath!!)
                project.logger.warn("${it.published.path}: ${it.validationResults!!.requiredTestCount} ${it.question.path} (file://$fullPath)")
            }
    }
}
