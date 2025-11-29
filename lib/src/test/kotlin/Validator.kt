package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.serialization.json

object Validator {
    private val questions =
        json.decodeFromString<List<Question>>(object {}::class.java.getResource("/questions.json")!!.readText())
            .associateBy { question -> question.published.name }

    suspend fun validate(name: String): Pair<Question, ValidationReport?> {
        val question = questions[name] ?: error("no question named $name")
        question.warm()
        return Pair(question, question.validate(124, 64))
    }
}