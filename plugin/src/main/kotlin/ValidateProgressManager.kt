package edu.illinois.cs.cs125.questioner.plugin

import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe progress manager for validate tasks.
 *
 * Handles parallel task execution by using atomic operations:
 * - AtomicBoolean ensures only one task starts the progress bar
 * - AtomicInteger ensures thread-safe counting
 *
 * Only shows progress if at least one task actually runs (not UP-TO-DATE).
 */
class ValidateProgressManager(
    private val progressLoggerFactory: ProgressLoggerFactory,
    private val totalQuestions: Int,
) {
    private var progressLogger: ProgressLogger? = null
    private val started = AtomicBoolean(false)
    private val tasksCompleted = AtomicInteger(0)
    private val tasksSkipped = AtomicInteger(0) // UP-TO-DATE tasks

    /**
     * Called at the start of each task's @TaskAction.
     * Only the first caller starts the progress bar.
     */
    fun taskStarting() {
        if (started.compareAndSet(false, true)) {
            progressLogger = progressLoggerFactory.newOperation(ValidateProgressManager::class.java).also {
                it.description = "Validating questions"
                it.started()
                it.progress(formatProgress())
            }
        }
    }

    /**
     * Called after each task completes its work.
     */
    fun taskCompleted() {
        tasksCompleted.incrementAndGet()
        progressLogger?.progress(formatProgress())
    }

    /**
     * Called when a task is UP-TO-DATE (from afterTask callback).
     */
    fun taskSkipped() {
        tasksSkipped.incrementAndGet()
        // Update progress if bar is already showing
        progressLogger?.progress(formatProgress())
    }

    private fun formatProgress(): String {
        val completed = tasksCompleted.get()
        val skipped = tasksSkipped.get()
        val activeTotal = totalQuestions - skipped
        val progressBar = buildProgressBar(completed, activeTotal, 20)
        return "$progressBar $completed/$activeTotal"
    }

    private fun buildProgressBar(current: Int, total: Int, width: Int): String {
        if (total == 0) return "[${"=".repeat(width)}]"
        val filled = (current.toDouble() / total * width).toInt().coerceIn(0, width)
        val empty = width - filled
        return "[" + "=".repeat(filled) + " ".repeat(empty) + "]"
    }

    /**
     * Called from validationReport task to complete the progress bar.
     */
    fun finish() {
        progressLogger?.completed()
        progressLogger = null
    }

    companion object {
        private var currentManager: ValidateProgressManager? = null

        /**
         * Initialize a fresh manager for this build.
         * Must be called at the start of each build (from afterEvaluate).
         */
        @Synchronized
        fun initialize(progressLoggerFactory: ProgressLoggerFactory, totalQuestions: Int) {
            currentManager = ValidateProgressManager(progressLoggerFactory, totalQuestions)
        }

        /**
         * Get the current manager instance.
         */
        fun getInstance(): ValidateProgressManager? = currentManager
    }
}
