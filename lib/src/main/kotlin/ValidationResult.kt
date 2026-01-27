@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.suppressionComment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Validation phase indicates which stage of validation produced this result.
 */
@Serializable
enum class ValidationPhase {
    @SerialName("validate")
    VALIDATE,

    @SerialName("calibrate")
    CALIBRATE
}

/**
 * JSON-serializable result of a validation attempt.
 * Can be either Success (validation passed) or Failure (validation failed with detailed error).
 */
@Serializable
sealed class ValidationResult {
    abstract val questionPath: String
    abstract val questionName: String
    abstract val questionAuthor: String
    abstract val questionSlug: String
    abstract val phase: ValidationPhase
    abstract val timestamp: Long
    abstract val durationMs: Long

    /**
     * Get a display string for the question: "Name (author/slug)"
     */
    fun questionDisplayName(): String = "$questionName ($questionAuthor/$questionSlug)"

    /**
     * Successful validation result.
     */
    @Serializable
    @SerialName("success")
    data class Success(
        override val questionPath: String,
        override val questionName: String,
        override val questionAuthor: String,
        override val questionSlug: String,
        override val phase: ValidationPhase,
        override val timestamp: Long,
        override val durationMs: Long,
        val summary: SuccessSummary,
    ) : ValidationResult()

    /**
     * Failed validation result with detailed error information.
     */
    @Serializable
    @SerialName("failure")
    data class Failure(
        override val questionPath: String,
        override val questionName: String,
        override val questionAuthor: String,
        override val questionSlug: String,
        override val phase: ValidationPhase,
        override val timestamp: Long,
        override val durationMs: Long,
        val error: ValidationError,
    ) : ValidationResult()
}

/**
 * Summary information for a successful validation.
 */
@Serializable
data class SuccessSummary(
    val seed: Int,
    val retries: Int,
    val testCount: Int,
    val requiredTestCount: Int,
    val requiredTime: Int,
    val mutationCount: Int,
    val hasKotlin: Boolean,
    val testingSequence: List<String>?,
)

/**
 * Sealed class representing all possible validation errors.
 * Maps 1:1 from the ValidationFailed exception hierarchy, preserving all context.
 */
@Serializable
sealed class ValidationError {
    abstract val errorType: String
    abstract val message: String
    abstract val testingSequence: List<String>?

    /** Primary source file involved in this error (for console output). */
    open val sourceFilePath: String? get() = null

    /**
     * Solution failed the test suites.
     */
    @Serializable
    @SerialName("solution_failed")
    data class SolutionFailed(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val explanation: String,
        val retries: Int,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "SolutionFailed"
        override val message = "Solution failed the test suites after $retries retries: $explanation"
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Couldn't generate enough receivers during solution testing.
     */
    @Serializable
    @SerialName("solution_receiver_generation")
    data class SolutionReceiverGeneration(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val retries: Int,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "SolutionReceiverGeneration"
        override val message =
            "Couldn't generate enough receivers during testing after $retries retries. " +
                "Examine any @FilterParameters methods or exceptions thrown in your constructor."
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Solution failed linting (checkstyle or ktlint).
     */
    @Serializable
    @SerialName("solution_failed_linting")
    data class SolutionFailedLinting(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val lintingErrors: String,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "SolutionFailedLinting"
        override val message = "Solution failed linting: $lintingErrors"
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Solution threw an unexpected exception.
     */
    @Serializable
    @SerialName("solution_threw")
    data class SolutionThrew(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val thrownException: String,
        val parameters: String,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "SolutionThrew"
        override val message = "Solution threw unexpected exception $thrownException on parameters $parameters"
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Testing framework threw an exception during solution testing.
     */
    @Serializable
    @SerialName("solution_testing_threw")
    data class SolutionTestingThrew(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val thrownException: String,
        val stackTrace: String,
        val output: String,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "SolutionTestingThrew"
        override val message = "Solution testing threw an exception: $thrownException"
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Solution lacks entropy - random inputs generated insufficient distinct results.
     */
    @Serializable
    @SerialName("solution_lacks_entropy")
    data class SolutionLacksEntropy(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val inputCount: Int,
        val distinctResults: Int,
        val executableName: String,
        val fauxStatic: Boolean,
        val resultSample: String?,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "SolutionLacksEntropy"
        override val message =
            "$inputCount inputs to $executableName only generated $distinctResults distinct results. " +
                "You may need to add or adjust your @RandomParameters method."
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Solution contains too much dead (untested) code.
     */
    @Serializable
    @SerialName("solution_dead_code")
    data class SolutionDeadCode(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val deadCodeLines: Int,
        val maximumAllowed: Int,
        val deadLineNumbers: List<Int>,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "SolutionDeadCode"
        override val message =
            "Solution contains $deadCodeLines lines of dead code, more than the maximum of $maximumAllowed. " +
                "Dead lines: ${deadLineNumbers.joinToString(", ")}"
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * No incorrect examples found or generated through mutation.
     */
    @Serializable
    @SerialName("no_incorrect")
    data class NoIncorrect(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
    ) : ValidationError() {
        override val errorType = "NoIncorrect"
        override val message = "No incorrect examples found or generated through mutation. Please add some using @Incorrect."
        override val testingSequence: List<String>? = null
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Too few mutations generated.
     */
    @Serializable
    @SerialName("too_few_mutations")
    data class TooFewMutations(
        val solutionCode: String,
        val solutionPath: String?,
        val solutionLanguage: String,
        val foundCount: Int,
        val neededCount: Int,
    ) : ValidationError() {
        override val errorType = "TooFewMutations"
        override val message =
            "Too few incorrect mutations generated: found $foundCount, needed $neededCount. " +
                "Please reduce the required number or remove mutation suppressions."
        override val testingSequence: List<String>? = null
        override val sourceFilePath: String? get() = solutionPath
    }

    /**
     * Submission generated too much output.
     */
    @Serializable
    @SerialName("too_much_output")
    data class TooMuchOutput(
        val sourceCode: String,
        val sourcePath: String?,
        val sourceLanguage: String,
        val outputSize: Int,
        val maxSize: Int,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "TooMuchOutput"
        override val message =
            "Submission generated too much output ($outputSize > $maxSize). " +
                "Consider reducing the number of tests using @Correct(minTestCount = NUM)."
        override val sourceFilePath: String? get() = sourcePath
    }

    /**
     * Incorrect code failed linting.
     */
    @Serializable
    @SerialName("incorrect_failed_linting")
    data class IncorrectFailedLinting(
        val incorrectCode: String,
        val incorrectPath: String?,
        val incorrectLanguage: String,
        val correctCode: String,
        val correctPath: String?,
        val lintingErrors: String,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "IncorrectFailedLinting"
        override val message = "Incorrect code failed linting: $lintingErrors"
        override val sourceFilePath: String? get() = incorrectPath
    }

    /**
     * Incorrect code passed the test suite (should have failed).
     */
    @Serializable
    @SerialName("incorrect_passed")
    data class IncorrectPassed(
        val incorrectCode: String,
        val incorrectPath: String?,
        val incorrectLanguage: String,
        val correctCode: String,
        val correctPath: String?,
        val isMutation: Boolean,
        val mutationType: String?,
        val suppressionComment: String?,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "IncorrectPassed"
        override val message = buildString {
            append("Incorrect code")
            if (isMutation) append(" (mutated)")
            append(" passed the test suites. ")
            append("If the code is incorrect, add an input to @FixedParameters to handle this case.")
        }
        override val sourceFilePath: String? get() = incorrectPath
    }

    /**
     * Incorrect code required too many tests to fail.
     */
    @Serializable
    @SerialName("incorrect_too_many_tests")
    data class IncorrectTooManyTests(
        val incorrectCode: String,
        val incorrectPath: String?,
        val incorrectLanguage: String,
        val correctCode: String,
        val correctPath: String?,
        val testsRequired: Int,
        val testsLimit: Int,
        val failingInput: String?,
        val isMutation: Boolean,
        val mutationType: String?,
        val suppressionComment: String?,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "IncorrectTooManyTests"
        override val message = buildString {
            append("Incorrect code eventually failed but required too many tests ($testsRequired > $testsLimit). ")
            failingInput?.let { append("We found failing inputs: $it. ") }
                ?: append("We were unable to find a failing input. ")
            append("If the code is incorrect, add an input to @FixedParameters to handle this case.")
        }
        override val sourceFilePath: String? get() = incorrectPath
    }

    /**
     * Incorrect code failed for the wrong reason.
     */
    @Serializable
    @SerialName("incorrect_wrong_reason")
    data class IncorrectWrongReason(
        val incorrectCode: String,
        val incorrectPath: String?,
        val incorrectLanguage: String,
        val expectedReason: String,
        val actualExplanation: String,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "IncorrectWrongReason"
        override val message =
            "Incorrect code failed but not for the expected reason. " +
                "Expected: $expectedReason, but found: $actualExplanation"
        override val sourceFilePath: String? get() = incorrectPath
    }

    /**
     * Testing threw an exception while testing incorrect code.
     */
    @Serializable
    @SerialName("incorrect_testing_threw")
    data class IncorrectTestingThrew(
        val incorrectCode: String,
        val incorrectPath: String?,
        val incorrectLanguage: String,
        val thrownException: String,
        val stackTrace: String,
        val output: String,
        override val testingSequence: List<String>?,
    ) : ValidationError() {
        override val errorType = "IncorrectTestingThrew"
        override val message = "Testing incorrect code threw an unexpected exception: $thrownException"
        override val sourceFilePath: String? get() = incorrectPath
    }

    /**
     * Unexpected error that doesn't fit other categories.
     */
    @Serializable
    @SerialName("unexpected_error")
    data class UnexpectedError(
        val exceptionType: String,
        val stackTrace: String,
        override val testingSequence: List<String>?,
        override val message: String,
    ) : ValidationError() {
        override val errorType = "UnexpectedError"
    }
}

/**
 * Extension function to convert a ValidationFailed exception to a ValidationError.
 */
fun ValidationFailed.toValidationError(): ValidationError = when (this) {
    is edu.illinois.cs.cs125.questioner.lib.SolutionFailed -> ValidationError.SolutionFailed(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        explanation = explanation,
        retries = retries,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.SolutionReceiverGeneration -> ValidationError.SolutionReceiverGeneration(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        retries = retries,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.SolutionFailedLinting -> ValidationError.SolutionFailedLinting(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        lintingErrors = errors,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.SolutionThrew -> ValidationError.SolutionThrew(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        thrownException = threw.toString(),
        parameters = parameters.toString(),
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.SolutionTestingThrew -> ValidationError.SolutionTestingThrew(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        thrownException = threw.toString(),
        stackTrace = threw.stackTraceToString(),
        output = output,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.SolutionLacksEntropy -> ValidationError.SolutionLacksEntropy(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        inputCount = count,
        distinctResults = amount,
        executableName = executable.name,
        fauxStatic = fauxStatic,
        resultSample = result?.toString(),
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.SolutionDeadCode -> ValidationError.SolutionDeadCode(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        deadCodeLines = amount,
        maximumAllowed = maximum,
        deadLineNumbers = dead,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.NoIncorrect -> ValidationError.NoIncorrect(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
    )

    is edu.illinois.cs.cs125.questioner.lib.TooFewMutations -> ValidationError.TooFewMutations(
        solutionCode = solution.contents,
        solutionPath = solution.path,
        solutionLanguage = solution.language.name,
        foundCount = found,
        neededCount = needed,
    )

    is edu.illinois.cs.cs125.questioner.lib.TooMuchOutput -> ValidationError.TooMuchOutput(
        sourceCode = contents,
        sourcePath = path,
        sourceLanguage = language.name,
        outputSize = size,
        maxSize = maxSize,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.IncorrectFailedLinting -> ValidationError.IncorrectFailedLinting(
        incorrectCode = incorrect.mutation?.marked()?.contents ?: incorrect.contents,
        incorrectPath = incorrect.path,
        incorrectLanguage = incorrect.language.name,
        correctCode = correct.contents,
        correctPath = correct.path,
        lintingErrors = errors,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.IncorrectPassed -> {
        val mutation = incorrect.mutation
        ValidationError.IncorrectPassed(
            incorrectCode = mutation?.marked()?.contents ?: incorrect.contents,
            incorrectPath = incorrect.path,
            incorrectLanguage = incorrect.language.name,
            correctCode = correct.contents,
            correctPath = correct.path,
            isMutation = mutation != null,
            mutationType = mutation?.mutations?.firstOrNull()?.mutation?.mutationType?.name,
            suppressionComment = mutation?.mutations?.firstOrNull()?.mutation?.mutationType?.suppressionComment(),
            testingSequence = testingSequence,
        )
    }

    is edu.illinois.cs.cs125.questioner.lib.IncorrectTooManyTests -> {
        val mutation = incorrect.mutation
        ValidationError.IncorrectTooManyTests(
            incorrectCode = mutation?.marked()?.contents ?: incorrect.contents,
            incorrectPath = incorrect.path,
            incorrectLanguage = incorrect.language.name,
            correctCode = correct.contents,
            correctPath = correct.path,
            testsRequired = testsRequired,
            testsLimit = testsLimit,
            failingInput = failingInput,
            isMutation = mutation != null,
            mutationType = mutation?.mutations?.firstOrNull()?.mutation?.mutationType?.name,
            suppressionComment = mutation?.mutations?.firstOrNull()?.mutation?.mutationType?.suppressionComment(),
            testingSequence = testingSequence,
        )
    }

    is edu.illinois.cs.cs125.questioner.lib.IncorrectWrongReason -> ValidationError.IncorrectWrongReason(
        incorrectCode = incorrect.contents,
        incorrectPath = incorrect.path,
        incorrectLanguage = incorrect.language.name,
        expectedReason = expected,
        actualExplanation = explanation,
        testingSequence = testingSequence,
    )

    is edu.illinois.cs.cs125.questioner.lib.IncorrectTestingThrew -> ValidationError.IncorrectTestingThrew(
        incorrectCode = incorrect.contents,
        incorrectPath = incorrect.path,
        incorrectLanguage = incorrect.language.name,
        thrownException = threw.toString(),
        stackTrace = threw.stackTraceToString(),
        output = output,
        testingSequence = testingSequence,
    )

    is RetryValidation -> {
        // RetryValidation wraps the actual exception
        val inner = cause
        if (inner is ValidationFailed) {
            inner.toValidationError()
        } else {
            ValidationError.UnexpectedError(
                exceptionType = inner?.javaClass?.simpleName ?: "Unknown",
                stackTrace = inner?.stackTraceToString() ?: "",
                testingSequence = testingSequence,
                message = inner?.message ?: "RetryValidation with unknown cause",
            )
        }
    }
}

/**
 * Extension function to convert a generic exception to a ValidationError.
 */
fun Throwable.toValidationError(): ValidationError = when (this) {
    is ValidationFailed -> toValidationError()
    else -> ValidationError.UnexpectedError(
        exceptionType = javaClass.simpleName,
        stackTrace = stackTraceToString(),
        testingSequence = null,
        message = message ?: "Unknown error",
    )
}

/**
 * Create a successful ValidationResult from a Question after phase 1 validation.
 * This is used when we only have access to the question with phase1Results, not a full ValidationReport.
 */
fun Question.toPhase1SuccessResult(
    questionPath: String,
    startTime: Long,
    seed: Int,
    retries: Int,
): ValidationResult.Success {
    val p1Results = phase1Results
        ?: error("Question must have phase1Results to create a success result")
    return ValidationResult.Success(
        questionPath = questionPath,
        questionName = published.name,
        questionAuthor = published.author,
        questionSlug = published.path,
        phase = ValidationPhase.VALIDATE,
        timestamp = startTime,
        durationMs = System.currentTimeMillis() - startTime,
        summary = SuccessSummary(
            seed = seed,
            retries = retries,
            testCount = p1Results.testCount,
            requiredTestCount = p1Results.testCount, // After phase 1, we don't have calibrated count yet
            requiredTime = 0, // Timing is determined in phase 2
            mutationCount = p1Results.mutationCount,
            hasKotlin = alternativeSolutions.any { it.language == Language.kotlin },
            testingSequence = null, // Testing sequence available in ValidationReport
        ),
    )
}

/**
 * Create a successful ValidationResult from a ValidationReport (phase 1 - deprecated, use Question.toPhase1SuccessResult).
 */
fun ValidationReport.toSuccessResult(
    questionPath: String,
    startTime: Long,
    seed: Int,
    retries: Int,
): ValidationResult.Success = ValidationResult.Success(
    questionPath = questionPath,
    questionName = question.published.name,
    questionAuthor = question.published.author,
    questionSlug = question.published.path,
    phase = ValidationPhase.VALIDATE,
    timestamp = startTime,
    durationMs = System.currentTimeMillis() - startTime,
    summary = SuccessSummary(
        seed = seed,
        retries = retries,
        testCount = correct.sumOf { it.results.tests()?.size ?: 0 },
        requiredTestCount = requiredTestCount,
        requiredTime = requiredTime,
        mutationCount = incorrect.count { it.incorrect.mutation != null },
        hasKotlin = hasKotlin,
        testingSequence = solutionTestingSequence,
    ),
)

/**
 * Create a successful ValidationResult from a CalibrationReport (phase 2).
 */
fun CalibrationReport.toSuccessResult(
    questionPath: String,
    startTime: Long,
): ValidationResult.Success = ValidationResult.Success(
    questionPath = questionPath,
    questionName = question.published.name,
    questionAuthor = question.published.author,
    questionSlug = question.published.path,
    phase = ValidationPhase.CALIBRATE,
    timestamp = startTime,
    durationMs = System.currentTimeMillis() - startTime,
    summary = SuccessSummary(
        seed = question.control.seed ?: Question.TestingControl.DEFAULT_SEED,
        retries = 0,
        testCount = correct.sumOf { it.results.tests()?.size ?: 0 },
        requiredTestCount = requiredTestCount,
        requiredTime = requiredTime,
        mutationCount = 0, // Calibration doesn't run mutations
        hasKotlin = hasKotlin,
        testingSequence = solutionTestingSequence,
    ),
)

/**
 * Create a failed ValidationResult from a ValidationFailed exception.
 */
fun ValidationFailed.toFailureResult(
    questionPath: String,
    questionName: String,
    questionAuthor: String,
    questionSlug: String,
    phase: ValidationPhase,
    startTime: Long,
): ValidationResult.Failure = ValidationResult.Failure(
    questionPath = questionPath,
    questionName = questionName,
    questionAuthor = questionAuthor,
    questionSlug = questionSlug,
    phase = phase,
    timestamp = startTime,
    durationMs = System.currentTimeMillis() - startTime,
    error = toValidationError(),
)

/**
 * Create a failed ValidationResult from any exception.
 */
fun Throwable.toFailureResult(
    questionPath: String,
    questionName: String,
    questionAuthor: String,
    questionSlug: String,
    phase: ValidationPhase,
    startTime: Long,
): ValidationResult.Failure = ValidationResult.Failure(
    questionPath = questionPath,
    questionName = questionName,
    questionAuthor = questionAuthor,
    questionSlug = questionSlug,
    phase = phase,
    timestamp = startTime,
    durationMs = System.currentTimeMillis() - startTime,
    error = toValidationError(),
)
