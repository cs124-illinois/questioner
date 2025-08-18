package edu.illinois.cs.cs125.questioner.server

import com.squareup.moshi.Types
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi

object Loader {
    val questions =
        moshi.adapter<List<Question>>(Types.newParameterizedType(List::class.java, Question::class.java))
            .fromJson(object {}::class.java.getResource("/questions.json")!!.readText())!!
            .associateBy { question -> question.published.path }

    fun getByPath(path: String) = questions[path]
}
