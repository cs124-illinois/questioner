package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.serialization.json

object Validator {
    private val questions =
        json.decodeFromString<List<Question>>(object {}::class.java.getResource("/questions.json")!!.readText())
            .associateBy { question -> question.published.name }

    // For unit testing: runs both validation and calibration in sequence
    // Note: This is only for testing - production use should run calibration in a no-JIT JVM
    suspend fun validateAndCalibrate(name: String): Pair<Question, CalibrationReport?> {
        val question = questions[name] ?: error("no question named $name")
        question.warm()
        question.validate(124, 64, 0, false)
        return Pair(question, question.calibrate())
    }
}