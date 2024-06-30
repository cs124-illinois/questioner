package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.Question
import java.util.function.BiPredicate

@Suppress("UNUSED")
open class QuestionerConfigExtension {
    var maxMutationCount: Int = 256
    var retries: Int = 4
    var verbose: Boolean = false
    var shuffleTests: Boolean = false
    var ignorePackages = listOf(
        "com.github.cs124_illinois.questioner.examples.",
        "com.github.cs124_illinois.questioner.testing.",
        "com.examples.",
    )
    var publishIncludes: BiPredicate<QuestionerConfig.EndPoint, Question> = BiPredicate { _, _ -> true }
    fun configPublishIncludes(method: BiPredicate<QuestionerConfig.EndPoint, Question>) {
        publishIncludes = method
    }
}
