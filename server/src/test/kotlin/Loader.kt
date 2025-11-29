package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.serialization.json

object Loader {
    val questions =
        json.decodeFromString<List<Question>>(object {}::class.java.getResource("/questions.json")!!.readText())
            .associateBy { question -> question.published.path }

    fun getByPath(path: String) = questions[path]
}
