package edu.illinois.cs.cs125.questioner.plugin

import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe progress manager for validate tasks.
 *
 * Shows progress as completed / activeTotal, where activeTotal = total - skipped.
 * UP-TO-DATE tasks decrement the denominator so the bar reflects actual work remaining.
 * The bar starts when the first real task runs; skip events adjust the denominator beforehand.
 */
class ValidateProgressManager(
    private val progressLoggerFactory: ProgressLoggerFactory,
    private val totalQuestions: Int,
) {
    private var progressLogger: ProgressLogger? = null
    private val started = AtomicBoolean(false)
    private val tasksCompleted = AtomicInteger(0)
    private val tasksSkipped = AtomicInteger(0)

    fun taskStarting() {
        if (started.compareAndSet(false, true)) {
            progressLogger = progressLoggerFactory.newOperation(ValidateProgressManager::class.java).also {
                it.description = "Validating questions"
                it.started()
                it.progress(formatProgress())
            }
        }
    }

    fun taskCompleted() {
        tasksCompleted.incrementAndGet()
        progressLogger?.progress(formatProgress())
    }

    fun taskSkipped() {
        tasksSkipped.incrementAndGet()
        progressLogger?.progress(formatProgress())
    }

    private fun formatProgress(): String {
        val completed = tasksCompleted.get()
        val activeTotal = totalQuestions - tasksSkipped.get()
        val progressBar = buildProgressBar(completed, activeTotal, 20)
        return "$progressBar $completed/$activeTotal"
    }

    private fun buildProgressBar(current: Int, total: Int, width: Int): String {
        if (total == 0) return "[${"=".repeat(width)}]"
        val filled = (current.toDouble() / total * width).toInt().coerceIn(0, width)
        val empty = width - filled
        return "[" + "=".repeat(filled) + " ".repeat(empty) + "]"
    }

    fun finish() {
        progressLogger?.completed()
        progressLogger = null
    }

    companion object {
        private var currentManager: ValidateProgressManager? = null

        @Synchronized
        fun initialize(progressLoggerFactory: ProgressLoggerFactory, totalQuestions: Int) {
            currentManager = ValidateProgressManager(progressLoggerFactory, totalQuestions)
        }

        fun getInstance(): ValidateProgressManager? = currentManager
    }
}
