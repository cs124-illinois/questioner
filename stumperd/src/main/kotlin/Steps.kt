package edu.illinois.cs.cs124.stumperd

enum class Steps(val value: Int) {
    IDENTIFY(1),
    DEDUPLICATE(2),
    CLEAN(3),
    REDEDUPLICATE(4),
    VALIDATE(5),
    MUTATE(6),
    DEDUPLICATE_MUTANTS(7);

    fun toException() = when (this) {
        IDENTIFY -> IdentifyFailure::class
        DEDUPLICATE -> DeduplicateFailure::class
        CLEAN -> CleaningFailure::class
        REDEDUPLICATE -> RededuplicateFailure::class
        VALIDATE -> ValidationFailure::class
        MUTATE -> MutationFailure::class
        DEDUPLICATE_MUTANTS -> DeduplicateMutantsFailure::class
    }

    companion object {
        fun forLimit(limit: Int) = entries.filter { it.value <= limit }.sortedBy { it.value }
    }
}


