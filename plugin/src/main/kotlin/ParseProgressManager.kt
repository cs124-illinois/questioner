package edu.illinois.cs.cs125.questioner.plugin

import org.gradle.api.Project
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages progress tracking for question parsing.
 */
class ParseProgressManager(
    private val progressLoggerFactory: ProgressLoggerFactory,
    private val totalQuestions: Int,
) {
    private var progressLogger: ProgressLogger? = null
    private val completed = AtomicInteger(0)

    @Synchronized
    fun start() {
        if (progressLogger == null) {
            progressLogger = progressLoggerFactory.newOperation(ParseProgressManager::class.java).also {
                it.description = "Parsing questions"
                it.started()
                it.progress("0/$totalQuestions")
            }
        }
    }

    fun questionCompleted() {
        val done = completed.incrementAndGet()
        val progressBar = buildProgressBar(done, totalQuestions, 20)
        progressLogger?.progress("$progressBar $done/$totalQuestions")
    }

    private fun buildProgressBar(current: Int, total: Int, width: Int): String {
        if (total == 0) return "[${"=".repeat(width)}]"
        val filled = (current.toDouble() / total * width).toInt().coerceIn(0, width)
        val empty = width - filled
        return "[" + "=".repeat(filled) + " ".repeat(empty) + "]"
    }

    @Synchronized
    fun finish() {
        progressLogger?.completed()
        progressLogger = null
    }

    companion object {
        private val managers = mutableMapOf<Project, ParseProgressManager>()

        @Synchronized
        fun initialize(
            project: Project,
            progressLoggerFactory: ProgressLoggerFactory,
            totalQuestions: Int,
        ): ParseProgressManager = managers.getOrPut(project.rootProject) {
            ParseProgressManager(progressLoggerFactory, totalQuestions)
        }

        @Synchronized
        fun getInstance(project: Project): ParseProgressManager? = managers[project.rootProject]

        @Synchronized
        fun remove(project: Project) {
            managers.remove(project.rootProject)
        }
    }
}
