package edu.illinois.cs.cs125.questioner.plugin.parse

import edu.illinois.cs.cs125.questioner.plugin.otherFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

fun Path.buildPackageMap(): Map<String, List<String>> {
    val otherFiles = getOtherFiles()
    check(otherFiles.none { it.endsWith(".kt") }) { "No support for Kotlin library files yet" }
    return otherFiles.groupBy { ParsedJavaFile(it.toFile()).packageName }
        .mapValues { (_, paths) -> paths.map { path -> path.toString() } }
}

fun Path.getOtherFiles() = Files.walk(this)
    .filter { path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt") }
    .map { path -> path.toFile() }
    .collect(Collectors.toList())
    .otherFiles()
    .filter { file ->
        when {
            file.name.endsWith(".java") -> !ParsedJavaFile(file).isQuestioner
            file.name.endsWith(".kt") -> !ParsedKotlinFile(file).isQuestioner
            else -> false
        }
    }.mapNotNull { file ->
        Paths.get(file.absolutePath)
    }.toList()
