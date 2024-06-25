package edu.illinois.cs.cs125.questioner.lib

import java.nio.file.Path
import kotlin.io.path.deleteIfExists

private inline fun retryOn(
    retries: Int,
    startSeed: Int,
    method: (retries: Int, currentSeed: Int) -> ValidationReport
): Pair<ValidationReport, Int> {
    var currentSeed = startSeed
    for (retry in 0..retries) {
        try {
            return Pair(method(retry, currentSeed), retry)
        } catch (e: Exception) {
            if (e is RetryValidation && retry != retries) {
                if (!e.timeout) {
                    currentSeed++
                }
                continue
            }
            throw e
        }
    }
    error("Shouldn't get here")
}

data class ValidatorOptions(val maxMutationCount: Int, val retries: Int, val verbose: Boolean, val rootDirectory: String)

@Suppress("unused")
suspend fun String.validate(options: ValidatorOptions) {
    val (maxMutationCount, retries, verbose, rootDirectory) = options

    gradleRootDirectory = rootDirectory

    val file = Path.of(this).toFile()
    check(file.exists())

    val question = file.loadQuestion()
    check(question != null)

    val reportPath = Path.of(this).parent.resolve("report.html")

    val startSeed = if (question.control.seed!! != -1) {
        question.control.seed!!
    } else {
        Question.TestingControl.DEFAULT_SEED
    }

    val (report, retried) = try {
        retryOn(retries, startSeed) { retry, seed -> question.validate(seed, maxMutationCount, retry, verbose) }
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
    println("${question.published.name}: ${report.summary} ($retried retries)")
}
