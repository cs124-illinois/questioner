@file:Suppress("ktlint:standard:filename")

package edu.illinois.cs.cs124.stumperd

sealed class StumperFailure(val step: Steps, cause: Throwable) : Exception(cause)

data class FailureCounts(
    val correctCount: Int,
    val identifyFailureCount: Int = 0,
    val deduplicateFailureCount: Int = 0,
    val cleanFailureCount: Int = 0,
    val rededuplicateFailureCount: Int = 0,
    val validationFailureCount: Int = 0,
    val mutationFailureCount: Int = 0,
    val deduplicateMutantsFailureCount: Int = 0,
    val validateMutantsFailureCount: Int = 0
) {
    fun failureCount(stepLimit: Int) = Steps.forLimit(stepLimit).sumOf { step -> countForStep(step) }
    fun successCount(stepLimit: Int) = correctCount - failureCount(stepLimit)

    fun countForStep(step: Steps) = when (step) {
        Steps.IDENTIFY -> identifyFailureCount
        Steps.DEDUPLICATE -> deduplicateFailureCount
        Steps.CLEAN -> cleanFailureCount
        Steps.REDEDUPLICATE -> rededuplicateFailureCount
        Steps.VALIDATE -> validationFailureCount
        Steps.MUTATE -> mutationFailureCount
        Steps.DEDUPLICATE_MUTANTS -> deduplicateMutantsFailureCount
        Steps.VALIDATE_MUTANTS -> validateMutantsFailureCount
    }
}
