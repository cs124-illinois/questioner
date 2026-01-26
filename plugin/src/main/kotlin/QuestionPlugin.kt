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

        project.tasks.register("parse", ParseQuestion::class.java) { task ->
            task.group = "questioner"
            task.description = "Parse this question's metadata"

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
                project.rootProject.layout.buildDirectory.file("questioner/questions/$hash.parsed.json"),
            )

            task.questionerVersion.set(VERSION)

            // Depend on package map being built
            task.dependsOn(":buildPackageMap")
        }

        // Get the question hash and output file path for validation tasks
        val hash = project.name.removePrefix("question-")
        val parsedFile = project.rootProject.layout.buildDirectory
            .file("questioner/questions/$hash.parsed.json").get().asFile

        // Validate this question (runs both phases: validate + calibrate)
        project.tasks.register("validate", TestQuestionTask::class.java) { task ->
            task.group = "questioner"
            task.description = "Validate this question"
            task.questionFilePath.set(parsedFile.absolutePath)
            task.parsedFile.set(parsedFile)
            task.validatedFile.set(
                project.rootProject.layout.buildDirectory.file("questioner/questions/$hash.validated.json"),
            )
            task.calibratedFile.set(
                project.rootProject.layout.buildDirectory.file("questioner/questions/$hash.calibrated.json"),
            )
            task.dependsOn(project.tasks.named("parse"))
        }
    }
}

/**
 * Task that runs both validation phases (validate + calibrate) for a question.
 * Does not fail the build on validation errors - failures are tracked and reported at the end.
 */
abstract class TestQuestionTask : DefaultTask() {
    @get:Internal
    abstract val questionFilePath: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val parsedFile: RegularFileProperty

    @get:OutputFile
    abstract val validatedFile: RegularFileProperty

    @get:OutputFile
    abstract val calibratedFile: RegularFileProperty

    @TaskAction
    fun test() {
        val serverManager = ValidationServerManager.getInstance(project.rootProject)
            ?: throw RuntimeException("ValidationServerManager not initialized. Run from root project.")

        val filePath = questionFilePath.get()

        // Phase 1: Validate (with JIT)
        ValidationClient.sendRequest(serverManager.getValidatePort(), filePath).onFailure { e ->
            serverManager.questionCompleted(false, filePath, "validate", e.message ?: "Unknown error")
            return
        }

        // Phase 2: Calibrate (without JIT)
        ValidationClient.sendRequest(serverManager.getCalibratePort(), filePath).onFailure { e ->
            serverManager.questionCompleted(false, filePath, "calibrate", e.message ?: "Unknown error")
            return
        }

        serverManager.questionCompleted(true)
    }
}

/**
 * Task to parse a single question directory and save its metadata.
 */
abstract class ParseQuestion : DefaultTask() {
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
