package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.verifiers.fromBase64
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/**
 * Result of a retry validation attempt, including the final seed used.
 */
data class RetryResult(val retries: Int, val finalSeed: Int)

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
// Reads from .parsed.json, writes to .validated.json
@Suppress("unused")
suspend fun String.validate(options: ValidatorOptions) {
    val (maxMutationCount, retries, verbose, rootDirectory) = options

    gradleRootDirectory = rootDirectory.fromBase64()

    val parsedPath = this
    val parsedFile = Path.of(parsedPath).toFile()
    check(parsedFile.exists()) { "Parsed file not found: $parsedPath" }

    // Skip if already completed validation (phase 1)
    val existingPhase1 = QuestionFiles.loadPhase1Result(parsedPath)
    if (existingPhase1 != null) {
        val question = parsedFile.loadQuestion()
        println("SKIPPED ${question?.published?.name ?: parsedPath}: Validation already complete")
        return
    }

    val question = parsedFile.loadQuestion()
    check(question != null) { "Could not load question from $parsedPath" }

    val reportPath = Path.of(parsedPath).parent.resolve("report.html")

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
    }

    // Save phase 1 results to .validated.json
    check(question.phase1Results != null) { "Question should have phase1Results after validation" }
    QuestionFiles.savePhase1Result(parsedPath, Phase1ValidationResult(question.phase1Results!!))

    println("${question.published.name}: Validation complete ($retried retries)")
}

// Phase 2: Calibration only (runs without JIT for consistent memory measurements)
// Reads from .parsed.json + .validated.json, writes to .calibrated.json
@Suppress("unused")
suspend fun String.calibrate(options: ValidatorOptions) {
    val (_, _, _, rootDirectory) = options

    gradleRootDirectory = rootDirectory.fromBase64()

    val parsedPath = this
    val parsedFile = Path.of(parsedPath).toFile()
    check(parsedFile.exists()) { "Parsed file not found: $parsedPath" }

    // Skip if already calibrated (fully validated)
    val existingPhase2 = QuestionFiles.loadPhase2Result(parsedPath)
    if (existingPhase2 != null) {
        val question = parsedFile.loadQuestion()
        println("SKIPPED ${question?.published?.name ?: parsedPath}: Already calibrated")
        return
    }

    // Load parsed question
    val question = parsedFile.loadQuestion()
    check(question != null) { "Could not load question from $parsedPath" }

    // Load phase 1 results and apply to question
    val phase1Result = QuestionFiles.loadPhase1Result(parsedPath)
    check(phase1Result != null) { "Validation must be completed before calibration for ${question.published.name}" }
    question.phase1Results = phase1Result.phase1Results

    val reportPath = Path.of(parsedPath).parent.resolve("report.html")

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
    }

    // Save phase 2 results to .calibrated.json
    check(question.testingSettings != null) { "Question should have testingSettings after calibration" }
    check(question.validationResults != null) { "Question should have validationResults after calibration" }
    QuestionFiles.savePhase2Result(
        parsedPath,
        Phase2CalibrationResult(
            testingSettings = question.testingSettings!!,
            testTestingLimits = question.testTestingLimits,
            validationResults = question.validationResults!!,
        ),
    )

    reportPath.toFile().writeText(report.report())
    println("${question.published.name}: ${report.summary}")
}

/**
 * Sealed class representing the result of a validation attempt with additional context.
 */
sealed class ValidateResult {
    data class Success(val question: Question, val retries: Int, val seed: Int) : ValidateResult()
    data class Failure(val error: ValidationFailed, val question: Question) : ValidateResult()
    data class Skipped(val questionName: String) : ValidateResult()
}

/**
 * Sealed class representing the result of a calibration attempt with additional context.
 */
sealed class CalibrateResult {
    data class Success(val report: CalibrationReport) : CalibrateResult()
    data class Failure(val error: ValidationFailed, val question: Question) : CalibrateResult()
    data class Skipped(val questionName: String) : CalibrateResult()
}

/**
 * Phase 1 validation that returns a result instead of throwing.
 * This allows the caller to handle both success and failure cases and generate appropriate reports.
 */
@Suppress("unused")
suspend fun String.validateWithResult(options: ValidatorOptions): ValidateResult {
    val (maxMutationCount, retries, verbose, rootDirectory) = options

    gradleRootDirectory = rootDirectory.fromBase64()

    val parsedPath = this
    val parsedFile = Path.of(parsedPath).toFile()
    check(parsedFile.exists()) { "Parsed file not found: $parsedPath" }

    // Skip if already completed validation (phase 1)
    val existingPhase1 = QuestionFiles.loadPhase1Result(parsedPath)
    if (existingPhase1 != null) {
        val question = parsedFile.loadQuestion()
        return ValidateResult.Skipped(question?.published?.name ?: parsedPath)
    }

    val question = parsedFile.loadQuestion()
    check(question != null) { "Could not load question from $parsedPath" }

    val startSeed = if (question.control.seed!! != -1) {
        question.control.seed!!
    } else {
        Question.TestingControl.DEFAULT_SEED
    }

    // Track both retry count and final seed
    var currentSeed = startSeed
    var retried = 0

    try {
        for (retry in 0..retries) {
            try {
                question.validate(currentSeed, maxMutationCount, retry, verbose)
                retried = retry
                break
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
    } catch (wrap: RetryValidation) {
        val e = wrap.cause as ValidationFailed
        return ValidateResult.Failure(e, question)
    } catch (e: ValidationFailed) {
        return ValidateResult.Failure(e, question)
    }

    // Save phase 1 results to .validated.json
    check(question.phase1Results != null) { "Question should have phase1Results after validation" }
    QuestionFiles.savePhase1Result(parsedPath, Phase1ValidationResult(question.phase1Results!!))

    return ValidateResult.Success(question, retried, currentSeed)
}

/**
 * Phase 2 calibration that returns a result instead of throwing.
 * This allows the caller to handle both success and failure cases and generate appropriate reports.
 */
@Suppress("unused")
suspend fun String.calibrateWithResult(options: ValidatorOptions): CalibrateResult {
    val (_, _, _, rootDirectory) = options

    gradleRootDirectory = rootDirectory.fromBase64()

    val parsedPath = this
    val parsedFile = Path.of(parsedPath).toFile()
    check(parsedFile.exists()) { "Parsed file not found: $parsedPath" }

    // Skip if already calibrated (fully validated)
    val existingPhase2 = QuestionFiles.loadPhase2Result(parsedPath)
    if (existingPhase2 != null) {
        val question = parsedFile.loadQuestion()
        return CalibrateResult.Skipped(question?.published?.name ?: parsedPath)
    }

    // Load parsed question
    val question = parsedFile.loadQuestion()
    check(question != null) { "Could not load question from $parsedPath" }

    // Load phase 1 results and apply to question
    val phase1Result = QuestionFiles.loadPhase1Result(parsedPath)
    check(phase1Result != null) { "Validation must be completed before calibration for ${question.published.name}" }
    question.phase1Results = phase1Result.phase1Results

    val report = try {
        question.calibrate()
    } catch (wrap: RetryValidation) {
        val e = wrap.cause as ValidationFailed
        return CalibrateResult.Failure(e, question)
    } catch (e: ValidationFailed) {
        return CalibrateResult.Failure(e, question)
    }

    // Save phase 2 results to .calibrated.json
    check(question.testingSettings != null) { "Question should have testingSettings after calibration" }
    check(question.validationResults != null) { "Question should have validationResults after calibration" }
    QuestionFiles.savePhase2Result(
        parsedPath,
        Phase2CalibrationResult(
            testingSettings = question.testingSettings!!,
            testTestingLimits = question.testTestingLimits,
            validationResults = question.validationResults!!,
        ),
    )

    return CalibrateResult.Success(report)
}
