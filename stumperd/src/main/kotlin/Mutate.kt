package edu.illinois.cs.cs124.stumperd

import edu.illinois.cs.cs125.jeed.core.ALL
import edu.illinois.cs.cs125.jeed.core.Mutation
import edu.illinois.cs.cs125.jeed.core.allFixedMutations
import edu.illinois.cs.cs125.questioner.lib.deTemplate
import edu.illinois.cs.cs125.questioner.lib.templateSubmission
import kotlin.random.Random

data class MutateOptions(
    val seed: Int = 124,
    val ignoredMutations: Set<Mutation.Type> = setOf(
        Mutation.Type.REMOVE_METHOD,
        Mutation.Type.NULL_RETURN,
        Mutation.Type.PRIMITIVE_RETURN
    )
)

suspend fun Stumper.mutate(options: MutateOptions = MutateOptions()) = doStep(Stumper.Steps.MUTATE) {
    val template = question.getTemplate(language)

    val sourceToMutate = (template?.let {
        "// TEMPLATE_START\n$cleanedContents\n// TEMPLATE_END\n"
    } ?: cleanedContents).let { contents ->
        question.templateSubmission(contents, language)
    }

    val mutationsToUse = ALL - options.ignoredMutations
    val initialMutants = sourceToMutate
        .allFixedMutations(random = Random(options.seed), types = mutationsToUse)
        .asSequence()
        .map { mutatedSource ->
            val contents = (template?.let {
                mutatedSource.contents.deTemplate(template)
            } ?: mutatedSource.contents).trim()
            assert(mutatedSource.mutations.size == 1)
            Stumper.MutatedSolution(mutatedSource.mutations.first().mutation.mutationType, contents)
        }.filter { mutant ->
            mutant.contents != cleanedContents && mutant.contents.isNotBlank()
        }.distinctBy { mutant -> mutant.contents }
        .toSet()

    assert(initialMutants.distinctBy { it.type }.intersect(options.ignoredMutations).isEmpty()) {
        "Mutations include ignored mutations"
    }

    check(initialMutants.isNotEmpty()) { "Solution generated no mutants" }

    mutants = initialMutants
}