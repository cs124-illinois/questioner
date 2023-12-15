package edu.illinois.cs.cs125.questioner.lib

import com.squareup.moshi.Types
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi

object Validator {
    private val questions =
        moshi.adapter<List<Question>>(Types.newParameterizedType(List::class.java, Question::class.java))
            .fromJson(object {}::class.java.getResource("/questions.json")!!.readText())!!
            .associateBy { question -> question.name }

    suspend fun validate(name: String): Pair<Question, ValidationReport?> {
        val question = questions[name] ?: error("no question named $name")
        question.warm()
        return Pair(question, question.validate(124, 64))
    }
}