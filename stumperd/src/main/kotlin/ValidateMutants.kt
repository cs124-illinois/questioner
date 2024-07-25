package edu.illinois.cs.cs124.stumperd

import edu.illinois.cs.cs125.questioner.lib.test

suspend fun Stumper.validateMutants() = doStep(Stumper.Steps.VALIDATE_MUTANTS) {
    val question = warmedQuestionHash.get(question.published.contentHash) { question }
        .also { question ->
            question.warm()
        }

    val validatedMutants = mutants.filter { mutant ->
        val results = question.test(mutant.contents, language)
        results.complete.partial?.passedSteps?.fullyCorrect == false
    }.toSet()

    check(validatedMutants.isNotEmpty()) { "Solution generated no incorrect mutants" }

    mutants = validatedMutants
}
