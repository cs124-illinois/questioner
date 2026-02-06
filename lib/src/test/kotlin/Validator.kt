package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.serialization.json
import kotlinx.serialization.encodeToString

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

    // Simulates the production pipeline: validate and calibrate with a serialization
    // round-trip of Phase1Results between phases (as happens when running in separate JVMs)
    suspend fun validateAndCalibrateWithRoundTrip(name: String): Pair<Question, CalibrationReport?> {
        val question = freshQuestion(name)
        question.warm()
        question.validate(124, 64, 0, false)

        // Serialize Phase1ValidationResult to JSON, as the real pipeline does
        val phase1Json = json.encodeToString(Phase1ValidationResult(question.phase1Results!!))

        // Load a truly fresh copy via serialization round-trip (not the same object)
        val freshQuestion = freshQuestion(name)
        freshQuestion.warm()
        val phase1Result = json.decodeFromString<Phase1ValidationResult>(phase1Json)
        freshQuestion.phase1Results = phase1Result.phase1Results

        return Pair(freshQuestion, freshQuestion.calibrate())
    }

    private fun freshQuestion(name: String): Question {
        val questionJson = json.encodeToString(questions[name] ?: error("no question named $name"))
        return json.decodeFromString<Question>(questionJson)
    }
}