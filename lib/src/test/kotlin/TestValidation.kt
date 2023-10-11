package edu.illinois.cs.cs125.questioner.lib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Path

private val validator = Validator(
    Path.of(object {}::class.java.getResource("/questions.json")!!.toURI()).toFile(),
    "",
    seed = 124,
    maxMutationCount = 64
)

class TestValidation : StringSpec({
    "it should validate a question" {
        validator.validate("Equals 88", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
    "it should validate a recursive question" {
        validator.validate("Recursive Factorial", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
    "recursion with private helper should work" {
        val (question, _) = validator.validate("Private Recursive Helper", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
        val differentName =
            question.correctByLanguage[Language.java]!!.replace("rangeSumHelper", "rangeSumHelping")
        question.test(differentName, Language.java).also {
            it.checkAll()
        }
    }
    "equals with both and filter should work" {
        validator.validate("Cougar Feliform", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "stdin interleaving should work" {
        validator.validate("Input Interleaving Test", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "constructor @NotNull should work" {
        validator.validate("Test Constructor NotNull", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "empty constructor should work" {
        validator.validate("Test Empty Constructor", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "feature checks should work" {
        validator.validate("With Feature Check", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "getters and setters should work" {
        validator.validate("Classroom Getters and Setters", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "template imports should work" {
        validator.validate("With Template Imports", force = true, testing = true)
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "it should validate a question testing Kotlin faux properties" {
        validator.validate("Kotlin Faux Property Testing", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
})