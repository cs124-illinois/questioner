package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.verifiers.fromBase64
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

private inline fun retryOnValidation(
    retries: Int,
    startSeed: Int,
    method: (retries: Int, currentSeed: Int) -> Unit
): Int {
    var currentSeed = startSeed
    for (retry in 0..retries) {
        try {
            method(retry, currentSeed)
            return retry
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

// Phase 1: Bootstrap, mutation, and incorrect testing (runs with JIT for speed)
@Suppress("unused")
suspend fun String.validate(options: ValidatorOptions) {
    val (maxMutationCount, retries, verbose, rootDirectory) = options

    gradleRootDirectory = rootDirectory.fromBase64()

    val file = Path.of(this).toFile()
    check(file.exists())

    val question = file.loadQuestion()
    check(question != null)

    // Skip if already completed validation (phase 1)
    if (question.phase1Completed) {
        println("SKIPPED ${question.published.name}: Validation already complete")
        return
    }

    val reportPath = Path.of(this).parent.resolve("report.html")

    val startSeed = if (question.control.seed!! != -1) {
        question.control.seed!!
    } else {
        Question.TestingControl.DEFAULT_SEED
    }

    val retried = try {
        retryOnValidation(retries, startSeed) { retry, seed ->
            question.validate(seed, maxMutationCount, retry, verbose)
        }
    } catch (wrap: RetryValidation) {
        val e = wrap.cause as ValidationFailed
        reportPath.toFile().writeText(e.report(question))
        println("FAILED ${question.published.name}: Validation (${e.retries} retries, final requested retry)")
        throw e
    } catch (e: ValidationFailed) {
        reportPath.toFile().writeText(e.report(question))
        println("FAILED ${question.published.name}: Validation (${e.retries} retries)")
        throw e
    } catch (e: Exception) {
        reportPath.deleteIfExists()
        throw e
    } finally {
        question.writeToFile(file)
    }

    check(question.phase1Completed) { "Question should have validation complete" }
    println("${question.published.name}: Validation complete ($retried retries)")
}

// Phase 2: Calibration only (runs without JIT for consistent memory measurements)
@Suppress("unused")
suspend fun String.calibrate(options: ValidatorOptions) {
    val (_, _, _, rootDirectory) = options

    gradleRootDirectory = rootDirectory.fromBase64()

    val file = Path.of(this).toFile()
    check(file.exists())

    val question = file.loadQuestion()
    check(question != null)

    // Skip if already calibrated (fully validated)
    if (question.validated) {
        println("SKIPPED ${question.published.name}: Already calibrated")
        return
    }

    // Check validation (phase 1) is complete
    if (!question.phase1Completed) {
        error("Validation must be completed before calibration for ${question.published.name}")
    }

    val reportPath = Path.of(this).parent.resolve("report.html")

    val report = try {
        question.calibrate()
    } catch (wrap: RetryValidation) {
        val e = wrap.cause as ValidationFailed
        reportPath.toFile().writeText(e.report(question))
        println("FAILED ${question.published.name}: Calibration (${e.retries} retries, retry requested)")
        throw e
    } catch (e: ValidationFailed) {
        reportPath.toFile().writeText(e.report(question))
        println("FAILED ${question.published.name}: Calibration (${e.retries} retries)")
        throw e
    } catch (e: Exception) {
        reportPath.deleteIfExists()
        throw e
    } finally {
        question.writeToFile(file)
    }

    check(question.validated) { "Question should be calibrated" }
    reportPath.toFile().writeText(report.report())
    println("${question.published.name}: ${report.summary}")
}
