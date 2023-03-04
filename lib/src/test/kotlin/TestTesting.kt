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

class TestTesting : StringSpec({
    "it should test a question" {
        val (question) = validator.validate("Add One", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
        val incorrect = """
int addOne(int value) {
  return value + 2;
}""".trim()
        question.test(incorrect, Question.Language.java).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testing!!.passed shouldBe false
        }
    }
    "it should test a question with partial credit" {
        val (question) = validator.validate("Add One", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
        """
int addOne(int value) {
  if (value == 0) {
    return 1;
  } else if (value == 1) {
    return 2;
  }
  return value + 2;
}""".trim().also { incorrect ->
            question.test(incorrect, Question.Language.java).also { results ->
                results.failedSteps.size shouldBe 0
                results.complete.testing!!.passed shouldBe false

                results.complete.partial!!.passedTestCount!!.passed shouldBeGreaterThan 0
                results.complete.partial!!.passedMutantCount!!.passed shouldBe results.complete.partial!!.passedMutantCount!!.total
            }
        }
    }
    "it should test a question with steps" {
        val (question) = validator.validate("Add One", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
        """
int addOn(int value) {
  return value+1
}""".trim().also { incorrect ->
            question.test(incorrect, Question.Language.java).also { results ->
                results.complete.partial!!.passedSteps.also {
                    it.compiled shouldBe false
                    it.design shouldBe false
                    it.partiallyCorrect shouldBe false
                    it.fullyCorrect shouldBe false
                    it.quality shouldBe false
                }
            }
        }
        """
int addOn(int value) {
  return value+2;
}""".trim().also { incorrect ->
            question.test(incorrect, Question.Language.java).also { results ->
                results.complete.partial!!.passedSteps.also {
                    it.compiled shouldBe true
                    it.design shouldBe false
                    it.partiallyCorrect shouldBe false
                    it.fullyCorrect shouldBe false
                    it.quality shouldBe false
                }
            }
        }
        """
int addOne(int value) {
  return value+2;
}""".trim().also { incorrect ->
            question.test(incorrect, Question.Language.java).also { results ->
                results.complete.partial!!.passedSteps.also {
                    it.compiled shouldBe true
                    it.design shouldBe true
                    it.partiallyCorrect shouldBe false
                    it.fullyCorrect shouldBe false
                    it.quality shouldBe false
                }
            }
        }
        """
int addOne(int value) {
  if (value == 1) {
    return 2;
  }
  return value+2;
}""".trim().also { incorrect ->
            question.test(incorrect, Question.Language.java).also { results ->
                results.complete.partial!!.passedSteps.also {
                    it.compiled shouldBe true
                    it.design shouldBe true
                    it.partiallyCorrect shouldBe true
                    it.fullyCorrect shouldBe false
                    it.quality shouldBe false
                }
            }
        }
        """
int addOne(int value) {
  return value+1;
}""".trim().also { incorrect ->
            question.test(incorrect, Question.Language.java).also { results ->
                results.complete.partial!!.passedSteps.also {
                    it.compiled shouldBe true
                    it.design shouldBe true
                    it.partiallyCorrect shouldBe true
                    it.fullyCorrect shouldBe true
                    it.quality shouldBe false
                }
            }
        }
        """
int addOne(int value) {
  return value + 1;
}""".trim().also { incorrect ->
            question.test(incorrect, Question.Language.java).also { results ->
                results.complete.partial!!.passedSteps.also {
                    it.compiled shouldBe true
                    it.design shouldBe true
                    it.partiallyCorrect shouldBe true
                    it.fullyCorrect shouldBe true
                    it.quality shouldBe true
                }
            }
        }
    }
})