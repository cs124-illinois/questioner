package edu.illinois.cs.cs124.stumperd

import edu.illinois.cs.cs125.questioner.lib.test

class ValidateMutantsFailure(cause: Throwable) : StumperFailure(Steps.VALIDATE_MUTANTS, cause)

typealias ValidatedMutants = Mutated

suspend fun DeduplicatedMutants.validateMutants(): ValidatedMutants = try {
    val question = questionCache.get(validated.identified.question.published.contentHash) { validated.identified.question }
        .also { question ->
            question.warm()
        }

    val validatedMutants = mutants.filter { mutant ->
        val results = question.test(mutant.contents, validated.identified.submission.language)
        results.complete.partial?.passedSteps?.fullyCorrect == false
    }.toSet()

    check(validatedMutants.isNotEmpty()) { "Solution generated no incorrect mutants" }

    ValidatedMutants(validated, validatedMutants)
} catch (e: Exception) {
    throw ValidateMutantsFailure(e)
}
