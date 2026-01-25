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
 * This will scan src/main/java for @Correct annotations and create a subproject
 * for each question directory, enabling parallel builds and per-question caching.
 */
class QuestionerSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        // Discover questions in the source directory
        val sourceDir = File(settings.rootDir, "src/main/java")
        val questions = discoverQuestionsWithCollisionCheck(sourceDir)

        // Include each question as a subproject
        questions.forEach { question ->
            val projectName = "question-${question.hash}"
            settings.include(projectName)
            settings.project(":$projectName").projectDir = question.correctFile.parentFile
        }

        // Store discovered questions for use by the root project's build script
        settings.gradle.beforeProject { project ->
            if (project == project.rootProject) {
                project.extensions.extraProperties["questioner.discoveredQuestions"] = questions
                project.extensions.extraProperties["questioner.questionCount"] = questions.size
            }
        }
    }
}
