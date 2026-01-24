package edu.illinois.cs.cs125.questioner.lib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TestSimpleIfElse : StringSpec({
    "should validate Simple If Else" {
        val (question, report) = Validator.validate("Simple If Else")

        question.validated shouldBe true
        report shouldNotBe null

        val validationResults = question.validationResults
        validationResults shouldNotBe null
    }

    "should test correct solution" {
        val (question, _) = Validator.validate("Simple If Else")
        question.validated shouldBe true

        val correctSolution = question.getCorrect(Language.java)!!
        val results = question.test(correctSolution, Language.java)

        results.succeeded shouldBe true
        results.complete.memoryAllocation?.failed shouldBe false
    }
})
