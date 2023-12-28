package edu.illinois.cs.cs125.questioner.lib

import java.nio.file.Path
import kotlin.io.path.deleteIfExists

private inline fun retryOn(limit: Int, method: () -> ValidationReport): Pair<ValidationReport, Int> {
    for (retry in 0 until limit) {
        try {
            return Pair(method(), retry)
        } catch (e: Exception) {
            if (e is RetryValidation && retry != limit - 1) {
                continue
            }
            throw e.cause ?: e
        }
    }
    error("Shouldn't get here")
}

@Suppress("unused")
suspend fun String.validate(seed: Int, maxMutationCount: Int, retries: Int) {
    val file = Path.of(this).toFile()
    check(file.exists())

    val question = file.loadQuestion()
    check(question != null)

    val reportPath = Path.of(this).parent.resolve("report.html")
    val (report, retried) = try {
        retryOn(retries) { question.validate(seed, maxMutationCount) }
    } catch (e: Exception) {
        reportPath.deleteIfExists()
        throw e
    } finally {
        question.writeToFile(file)
    }

    check(question.validated) { "Question should be validated" }
    reportPath.toFile().writeText(report.report())
    println("${question.published.name}: ${report.summary} ($retried)")
}
