package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.plugin.parse.buildPackageMap
import kotlinx.serialization.encodeToString
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class BuildPackageMap : DefaultTask() {
    @Internal
    val sourceSet: SourceSet =
        project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")

    @Suppress("UNCHECKED_CAST")
    @Internal
    val allSourceDirs: List<File> = project.extensions.extraProperties.let { extra ->
        if (extra.has("questioner.sourceDirs")) {
            extra.get("questioner.sourceDirs") as? List<File>
        } else {
            null
        }
    } ?: listOf(sourceSet.java.srcDirs.first())

    @InputFiles
    @Suppress("unused")
    val inputFiles: Set<File> = allSourceDirs.flatMap { dir ->
        dir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".java") || it.name.endsWith(".kt")) }
            .toList()
    }.otherFiles().toSet()

    @OutputFile
    val outputFile: File = project.layout.buildDirectory.dir("questioner/packageMap.json").get().asFile

    @TaskAction
    fun build() {
        val merged = mutableMapOf<String, List<String>>()
        for (dir in allSourceDirs) {
            val packageMap = dir.toPath().buildPackageMap()
            for ((pkg, files) in packageMap) {
                merged[pkg] = (merged[pkg] ?: emptyList()) + files
            }
        }
        merged.writeToFile(outputFile)
    }
}

private fun Map<String, List<String>>.writeToFile(file: File) {
    file.writeText(json.encodeToString(this))
}

internal fun Collection<File>.otherFiles() = filter { file ->
    !file.readLines().any { line ->
        line.trim().let { trimmedLine ->
            trimmedLine.startsWith("import edu.illinois.cs.cs125.questioner.lib.Correct") ||
                trimmedLine.startsWith("import edu.illinois.cs.cs125.questioner.lib.Incorrect") ||
                trimmedLine.startsWith("import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect") ||
                trimmedLine.startsWith("import edu.illinois.cs.cs125.questioner.lib.Starter")
        }
    }
}
