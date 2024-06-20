package edu.illinois.cs.cs125.questioner.lib

import java.nio.file.Path
import kotlin.io.path.deleteIfExists

private inline fun retryOn(retries: Int, method: (retries: Int) -> ValidationReport): Pair<ValidationReport, Int> {
    for (retry in 0..retries) {
        try {
            return Pair(method(retry), retry)
        } catch (e: Exception) {
            if (e is RetryValidation && retry != retries) {
                continue
            }
            throw e
        }
    }
    error("Shouldn't get here")
}

@Suppress("unused")
suspend fun String.validate(seed: Int, maxMutationCount: Int, retries: Int, quiet: Boolean = false) {
    val file = Path.of(this).toFile()
    check(file.exists())

    val question = file.loadQuestion()
    check(question != null)

    val reportPath = Path.of(this).parent.resolve("report.html")
    val (report, retried) = try {
        retryOn(retries) { retry -> question.validate(seed, maxMutationCount, retry) }
    } catch (wrap: RetryValidation) {
        val e = wrap.cause as ValidationFailed
        reportPath.toFile().writeText(e.report(question))
        println("FAILED ${question.published.name}: (${e.retries} retries, final requested retry)")
        throw e
    } catch (e: ValidationFailed) {
        reportPath.toFile().writeText(e.report(question))
        println("FAILED ${question.published.name}: (${e.retries} retries)")
        throw e
    } catch (e: Exception) {
        reportPath.deleteIfExists()
        throw e
    } finally {
        question.writeToFile(file)
    }

    check(question.validated) { "Question should be validated" }
    reportPath.toFile().writeText(report.report())
    if (!quiet) {
        println("${question.published.name}: ${report.summary} ($retried retries)")
    }
}
