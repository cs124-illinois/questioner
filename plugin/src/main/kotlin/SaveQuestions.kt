package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.writeToFile
import edu.illinois.cs.cs125.questioner.plugin.parse.ParsedJavaFile
import edu.illinois.cs.cs125.questioner.plugin.parse.ParsedKotlinFile
import edu.illinois.cs.cs125.questioner.plugin.parse.parseDirectory
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import javax.inject.Inject

abstract class SaveQuestions : DefaultTask() {
    @Internal
    val sourceSet: SourceSet =
        project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")

    @Internal
    val sourceDirectorySet: MutableSet<File> = sourceSet.java.srcDirs

    @Internal
    val sourceDirectoryPath: Path = sourceDirectorySet.first().toPath()

    @Internal
    val correctFiles: List<Path> = sourceDirectoryPath.getCorrectFiles()

    @InputFiles
    @Suppress("unused")
    val inputFiles: FileCollection = sourceSet.allSource.filter { it.name.endsWith(".java") || it.name.endsWith(".kt") }

    @OutputFiles
    @Suppress("unused")
    val outputFiles: List<Path> = correctFiles.map { path -> path.parent.resolve(".question.json") }

    @Inject
    abstract fun getWorkerExecutor(): WorkerExecutor?

    @TaskAction
    fun save() {
        val workQueue = getWorkerExecutor()!!.noIsolation()
        correctFiles.forEach { path ->
            workQueue.submit(ParseDirectory::class.java) { parameters ->
                parameters.getBaseDirectory().set(sourceDirectoryPath.toFile())
                parameters.getCorrectDirectory().set(path.toFile())
            }
        }
    }
}

fun Path.getCorrectFiles() = Files.walk(this)
    .filter { path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt") }
    .map { path -> path.toFile() }
    .filter { file ->
        file.readText().lines().any { it.trim().startsWith("import edu.illinois.cs.cs125.questioner.lib.Correct") }
    }
    .filter { file ->
        when {
            file.name.endsWith(".java") -> ParsedJavaFile(file).isCorrect
            file.name.endsWith(".kt") -> ParsedKotlinFile(file).isCorrect
            else -> false
        }
    }.map { file ->
        Paths.get(file.absolutePath)
    }.collect(Collectors.toList()).filterNotNull().toList()

interface ParseDirectoryWorkParameters : WorkParameters {
    fun getCorrectDirectory(): RegularFileProperty

    fun getBaseDirectory(): RegularFileProperty
}

abstract class ParseDirectory : WorkAction<ParseDirectoryWorkParameters> {
    override fun execute() {
        val correctDirectory = parameters.getCorrectDirectory().get().asFile.toPath()
        val baseDirectory = parameters.getBaseDirectory().get().asFile.toPath()
        val questionPath = correctDirectory.parent.resolve(".question.json")

        val repeatCount = 2
        repeat(repeatCount) { i ->
            try {
                correctDirectory.parseDirectory(baseDirectory).writeToFile(questionPath.toFile())
                return
            } catch (e: Exception) {
                if (i == repeatCount - 1) {
                    questionPath.toFile().delete()
                    throw e
                }
            }
        }
    }
}
