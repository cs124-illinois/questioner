@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

open class ShowUpdatedSeeds : DefaultTask() {
    init {
        group = "Verification"
        description = "Show questions with updated seeds."
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
            .filter { it.testingSettings!!.seed != it.control.seed }
            .forEach {
                val fullPath = project.rootProject.projectDir.resolve(it.correctPath!!)
                project.logger.warn("${it.published.path}: update seed to ${it.testingSettings!!.seed} (file://$fullPath)")
            }
    }
}
