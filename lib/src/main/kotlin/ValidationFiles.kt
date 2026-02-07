package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.serialization.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Result of phase 1 validation (bootstrap, mutation, incorrect testing).
 * Written to {hash}.validated.json
 */
@Serializable
data class Phase1ValidationResult(
    val phase1Results: Question.Phase1Results,
)

/**
 * Result of phase 2 calibration.
 * Written to {hash}.calibrated.json
 */
@Serializable
data class Phase2CalibrationResult(
    val testingSettings: Question.TestingSettings,
    val testTestingLimits: Question.TestTestingLimits?,
    val validationResults: Question.ValidationResults,
    val loadedClassesByLanguage: Map<Language, Set<String>>? = null,
)

/**
 * File path utilities for split question files.
 */
object QuestionFiles {
    fun parsedPath(basePath: String): String = basePath.replace(".parsed.json", ".parsed.json")
    fun validatedPath(basePath: String): String = basePath.replace(".parsed.json", ".validated.json")
    fun calibratedPath(basePath: String): String = basePath.replace(".parsed.json", ".calibrated.json")

    fun loadParsedQuestion(parsedFile: File): Question? {
        if (!parsedFile.exists()) return null
        return parsedFile.loadQuestion()
    }

    fun loadPhase1Result(parsedPath: String): Phase1ValidationResult? {
        val file = File(validatedPath(parsedPath))
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Phase1ValidationResult>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun loadPhase2Result(parsedPath: String): Phase2CalibrationResult? {
        val file = File(calibratedPath(parsedPath))
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Phase2CalibrationResult>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun savePhase1Result(parsedPath: String, result: Phase1ValidationResult) {
        val file = File(validatedPath(parsedPath))
        file.writeText(json.encodeToString(result))
    }

    fun savePhase2Result(parsedPath: String, result: Phase2CalibrationResult) {
        val file = File(calibratedPath(parsedPath))
        file.writeText(json.encodeToString(result))
    }

    /**
     * Merge parsed question with validation and calibration results.
     */
    fun mergeQuestion(parsedPath: String): Question? {
        val parsedFile = File(parsedPath)
        val question = loadParsedQuestion(parsedFile) ?: return null

        // Apply phase 1 results if available
        loadPhase1Result(parsedPath)?.let { phase1 ->
            question.phase1Results = phase1.phase1Results
            question.classification.recursiveMethodsByLanguage = phase1.phase1Results.solutionRecursiveMethods
            question.testTestingIncorrect = phase1.phase1Results.testTestingIncorrect
        }

        // Apply phase 2 results if available
        loadPhase2Result(parsedPath)?.let { phase2 ->
            question.testingSettings = phase2.testingSettings
            question.testTestingLimits = phase2.testTestingLimits
            question.validationResults = phase2.validationResults
            question.classification.loadedClassesByLanguage = phase2.loadedClassesByLanguage
        }

        return question
    }
}
