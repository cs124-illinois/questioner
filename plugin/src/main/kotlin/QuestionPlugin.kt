package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.writeToFile
import edu.illinois.cs.cs125.questioner.plugin.parse.parseDirectory
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.relativeTo

/**
 * Convention plugin applied to each question subproject.
 * Registers the SaveQuestion task for parsing and saving question metadata.
 */
class QuestionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // This plugin is applied to question subprojects (question-<hash>)
        // The root project should have the package map and source directory info

        project.tasks.register("saveQuestion", SaveQuestion::class.java) { task ->
            task.group = "questioner"
            task.description = "Parse and save this question's metadata"

            // Find the @Correct file in this project directory
            val correctFile = project.projectDir.listFiles { file ->
                file.extension == "java" || file.extension == "kt"
            }?.firstOrNull { file ->
                file.readText().contains("@Correct")
            }

            if (correctFile != null) {
                task.correctFile.set(correctFile)
            }

            // The question directory is the project directory
            task.questionDirectory.set(project.projectDir)

            // Root project provides the base directory and package map
            task.baseDirectory.set(project.rootProject.layout.projectDirectory.dir("src/main/java"))
            task.rootDirectory.set(project.rootProject.layout.projectDirectory)
            task.packageMapFile.set(
                project.rootProject.layout.buildDirectory.file("questioner/packageMap.json"),
            )

            // Output goes to build directory with hashed filename
            val hash = project.name.removePrefix("question-")
            task.outputFile.set(
                project.rootProject.layout.buildDirectory.file("questioner/questions/$hash.question.json"),
            )

            task.questionerVersion.set(VERSION)

            // Depend on package map being built
            task.dependsOn(":buildPackageMap")
        }

        // Get the question hash and output file path for validation tasks
        val hash = project.name.removePrefix("question-")
        val questionFile = project.rootProject.layout.buildDirectory
            .file("questioner/questions/$hash.question.json").get().asFile

        // Phase 1: Validate this question
        project.tasks.register("validateQuestion", ValidateQuestionTask::class.java) { task ->
            task.group = "questioner"
            task.description = "Validate this question (phase 1)"
            task.questionFilePath.set(questionFile.absolutePath)
            task.mode.set("validate")
            task.dependsOn(project.tasks.named("saveQuestion"))
        }

        // Phase 2: Calibrate this question
        project.tasks.register("calibrateQuestion", ValidateQuestionTask::class.java) { task ->
            task.group = "questioner"
            task.description = "Calibrate this question (phase 2, no JIT)"
            task.questionFilePath.set(questionFile.absolutePath)
            task.mode.set("calibrate")
            task.mustRunAfter(project.tasks.named("validateQuestion"))
            task.dependsOn(project.tasks.named("saveQuestion"))
        }

        // Combined task to run both phases
        project.tasks.register("testQuestion") { task ->
            task.group = "questioner"
            task.description = "Run full validation for this question (both phases)"
            task.dependsOn("validateQuestion", "calibrateQuestion")
        }
    }
}

/**
 * Task that sends a validation request to the ValidationServer.
 */
abstract class ValidateQuestionTask : DefaultTask() {
    @get:Input
    abstract val questionFilePath: Property<String>

    @get:Input
    abstract val mode: Property<String>

    @TaskAction
    fun validate() {
        val serverManager = ValidationServerManager.getInstance(project.rootProject)
            ?: throw RuntimeException("ValidationServerManager not initialized. Run from root project.")

        val port = when (mode.get()) {
            "validate" -> serverManager.getValidatePort()
            "calibrate" -> serverManager.getCalibratePort()
            else -> throw IllegalArgumentException("Unknown mode: ${mode.get()}")
        }

        val result = ValidationClient.sendRequest(port, questionFilePath.get())
        result.onFailure { e ->
            throw RuntimeException("${mode.get()} failed for ${questionFilePath.get()}: ${e.message}", e)
        }
    }
}

/**
 * Task to parse a single question directory and save its metadata.
 */
abstract class SaveQuestion : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val correctFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val questionDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baseDirectory: DirectoryProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageMapFile: RegularFileProperty

    @get:Input
    abstract val questionerVersion: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun save() {
        val correctPath = correctFile.get().asFile.toPath()
        val baseDir = baseDirectory.get().asFile.toPath()
        val rootDir = rootDirectory.get().asFile.toPath()
        val packageMap = readPackageMap(packageMapFile.get().asFile)

        try {
            val question = correctPath.parseDirectory(
                baseDirectory = baseDir,
                inputPackageMap = packageMap,
                questionerVersion = questionerVersion.get(),
                rootDirectory = rootDir,
            )

            // Set the correct path relative to root
            question.correctPath = correctPath.relativeTo(rootDir).toString()

            // Ensure output directory exists
            outputFile.get().asFile.parentFile.mkdirs()

            // Write the question JSON
            question.writeToFile(outputFile.get().asFile)
        } catch (e: Exception) {
            throw RuntimeException("Problem parsing file://$correctPath", e)
        }
    }

    private fun readPackageMap(file: File): Map<String, List<String>> = edu.illinois.cs.cs125.questioner.lib.serialization.json.decodeFromString(file.readText())
}
