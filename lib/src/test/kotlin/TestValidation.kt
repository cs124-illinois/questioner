package edu.illinois.cs.cs125.questioner.lib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TestValidation : StringSpec({
    "it should validate a question" {
        Validator.validateAndCalibrate("Equals 88").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
    "it should validate a class question" {
        Validator.validateAndCalibrate("Add One").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
    "it should validate a recursive question" {
        Validator.validateAndCalibrate("Recursive Factorial").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
    "recursion with private helper should work" {
        val (question, _) = Validator.validateAndCalibrate("Private Recursive Helper")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
        val differentName =
            question.getCorrect(Language.java)!!.replace("rangeSumHelper", "rangeSumHelping")
        question.test(differentName, Language.java).also {
            it.checkAll()
        }
    }
    "equals with both and filter should work" {
        Validator.validateAndCalibrate("Cougar Feliform")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "stdin interleaving should work" {
        Validator.validateAndCalibrate("Input Interleaving Test")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "filesystem access should work" {
        Validator.validateAndCalibrate("Read Hello World!")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "constructor @NotNull should work" {
        Validator.validateAndCalibrate("Test Constructor NotNull")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "empty constructor should work" {
        Validator.validateAndCalibrate("Test Empty Constructor")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "feature checks should work" {
        Validator.validateAndCalibrate("With Feature Check")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "getters and setters should work" {
        Validator.validateAndCalibrate("Classroom Getters and Setters")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "template imports should work" {
        Validator.validateAndCalibrate("With Template Imports")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "it should validate a question testing Kotlin faux properties" {
        Validator.validateAndCalibrate("Kotlin Faux Property Testing").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
})