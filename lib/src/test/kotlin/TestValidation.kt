package edu.illinois.cs.cs125.questioner.lib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TestValidation : StringSpec({
    "it should validate a question" {
        Validator.validate("Equals 88").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
    "it should validate a recursive question" {
        Validator.validate("Recursive Factorial").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
    "recursion with private helper should work" {
        val (question, _) = Validator.validate("Private Recursive Helper")
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
        Validator.validate("Cougar Feliform")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "stdin interleaving should work" {
        Validator.validate("Input Interleaving Test")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "filesystem access should work" {
        Validator.validate("Read Hello World!")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "constructor @NotNull should work" {
        Validator.validate("Test Constructor NotNull")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "empty constructor should work" {
        Validator.validate("Test Empty Constructor")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "feature checks should work" {
        Validator.validate("With Feature Check")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "getters and setters should work" {
        Validator.validate("Classroom Getters and Setters")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "template imports should work" {
        Validator.validate("With Template Imports")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
    }
    "it should validate a question testing Kotlin faux properties" {
        Validator.validate("Kotlin Faux Property Testing").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
    }
})