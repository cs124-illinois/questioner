package edu.illinois.cs.cs125.questioner.plugin

import com.squareup.moshi.Types
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import edu.illinois.cs.cs125.questioner.plugin.parse.buildPackageMap
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path

abstract class BuildPackageMap : DefaultTask() {
    @Internal
    val sourceSet: SourceSet =
        project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")

    @Internal
    val sourceDirectorySet: MutableSet<File> = sourceSet.java.srcDirs

    @Internal
    val sourceDirectoryPath: Path = sourceDirectorySet.first().toPath()

    @InputFiles
    @Suppress("unused")
    val inputFiles: Set<File> =
        sourceSet.allSource.filter { it.name.endsWith(".java") || it.name.endsWith(".kt") }.files.otherFiles().toSet()

    @OutputFile
    val outputFile: File = project.layout.buildDirectory.dir("questioner/packageMap.json").get().asFile

    @TaskAction
    fun build() = sourceDirectoryPath.buildPackageMap().writeToFile(outputFile)
}

private fun Map<String, List<String>>.writeToFile(file: File) {
    file.writeText(
        moshi.adapter<Map<String, List<String>>>(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Types.newParameterizedType(List::class.java, String::class.java),
            ),
        ).indent("  ").toJson(this),
    )
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
