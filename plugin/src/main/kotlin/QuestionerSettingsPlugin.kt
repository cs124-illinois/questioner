package edu.illinois.cs.cs125.questioner.plugin

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

/**
 * Settings plugin that discovers question directories and includes them as subprojects.
 *
 * Apply this plugin in settings.gradle.kts:
 * ```
 * plugins {
 *     id("org.cs124.questioner.settings")
 * }
 * ```
 *
 * Optionally configure external source directories:
 * ```
 * questioner {
 *     external("external/alice")
 *     external("external/bob")
 * }
 * ```
 *
 * This will scan src/main/java (and any configured external directories) for @Correct
 * annotations and create a subproject for each question directory, enabling parallel
 * builds and per-question caching.
 */
class QuestionerSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = settings.extensions.create("questioner", QuestionerSettingsExtension::class.java)

        // Defer discovery until after settings.gradle.kts has been fully evaluated,
        // so the user's questioner { external(...) } calls have been applied.
        settings.gradle.settingsEvaluated {
            val primarySourceDir = File(settings.rootDir, "src/main/java")

            // Build the list of all source directories with their external slugs and excludes
            val sourceDirs = mutableListOf<Triple<File, String?, List<String>>>(Triple(primarySourceDir, null, emptyList()))
            for (externalDir in extension.externalDirs) {
                val externalSourceDir = File(settings.rootDir, "${externalDir.path}/src/main/java")
                if (!externalSourceDir.exists()) {
                    settings.gradle.rootProject { project ->
                        project.logger.warn(
                            "questioner: external directory does not exist: ${externalSourceDir.absolutePath}",
                        )
                    }
                } else {
                    sourceDirs.add(Triple(externalSourceDir, externalDir.path, externalDir.excludes))
                }
            }

            // Discover questions from all source directories
            val questions = discoverQuestionsFromDirs(sourceDirs)

            // Include each question as a subproject
            questions.forEach { question ->
                val projectName = "question-${question.hash}"
                settings.include(projectName)
                settings.project(":$projectName").projectDir = question.correctFile.parentFile
            }

            // Store discovered questions and source info for use by build plugins
            settings.gradle.beforeProject { project ->
                if (project == project.rootProject) {
                    project.extensions.extraProperties["questioner.discoveredQuestions"] = questions
                    project.extensions.extraProperties["questioner.questionCount"] = questions.size
                    project.extensions.extraProperties["questioner.sourceDirs"] =
                        sourceDirs.map { it.first }.filter { it.exists() }
                    project.extensions.extraProperties["questioner.questionSourceRoots"] =
                        questions.associate { it.hash to it.sourceRoot }
                    project.extensions.extraProperties["questioner.questionExternals"] =
                        questions.filter { it.external != null }.associate { it.hash to it.external }
                }
            }
        }
    }
}
