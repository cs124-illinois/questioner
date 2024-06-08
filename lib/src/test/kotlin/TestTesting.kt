package edu.illinois.cs.cs125.questioner.lib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain


class TestTesting : StringSpec({
    "it should test a question" {
        val (question) = Validator.validate("Add One").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
        val incorrect = """
int addOne(int value) {
  return value + 2;
}""".trim()

        question.test(incorrect, Language.java).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testing!!.passed shouldBe false
        }
    }
    "it should test a question with partial credit" {
        val (question) = Validator.validate("Add One").also { (question, report) ->
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
            question.test(incorrect, Language.java).also { results ->
                results.failedSteps.size shouldBe 0
                results.complete.testing!!.passed shouldBe false

                results.complete.partial!!.passedTestCount!!.passed shouldBeGreaterThan 0
                results.complete.partial!!.passedMutantCount!!.passed shouldBe results.complete.partial!!.passedMutantCount!!.total
            }
        }
    }
    "it should test a question with steps" {
        val (question) = Validator.validate("Add One").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }
        """
int addOn(int value) {
  return value+1
}""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also { results ->
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
            question.test(incorrect, Language.java).also { results ->
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
            question.test(incorrect, Language.java).also { results ->
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
  if (value == 0) {
    return 1;
  }
  return value+2;
}""".trim().also { incorrect ->
            question.test(incorrect, Language.java, question.testingSettings!!.copy(runAll = true)).also { results ->
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
            question.test(incorrect, Language.java).also { results ->
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
            question.test(incorrect, Language.java).also { results ->
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
    "it should identify incorrect submissions to a method problem" {
        val (question) = Validator.validate("Add One").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
            report!!.requiredTestCount shouldBeGreaterThan 0
        }

        """
int addOne(int value) {
  return value + 1;
}
System.out.println("Hello, world!");
""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also {
                it.failedSteps.size shouldBe 1
                it.failedSteps shouldContain TestResults.Step.checkInitialSubmission
                it.failed.checkInitialSubmission shouldContain "no code outside"
            }
        }
        """
int addOne(int value) {
  return value + 1;
}
class Testing { }
""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also {
                it.failedSteps.size shouldBe 1
                it.failedSteps shouldContain TestResults.Step.checkInitialSubmission
                it.failed.checkInitialSubmission shouldContain "Class declarations are not allowed"
            }
        }
    }
    "it should test a Map question with extra imports" {
        val (question) = Validator.validate("With Map Extras")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
        """
int mapSize(Map<String, Integer> map) {
  return map.size();
}
""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also { results ->
                results.complete.partial!!.passedSteps.quality shouldBe true
            }
        }
        """
int mapSize(Map<String, Integer> map) {
  return map.keySet().size();
}
""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also { results ->
                results.complete.partial!!.passedSteps.fullyCorrect shouldBe true
            }
        }
        """
int mapSize(Map<String, Integer> map) {
  Set<String> keys = map.keySet();
  return keys.size();
}
""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also { results ->
                results.complete.partial!!.passedSteps.fullyCorrect shouldBe true
            }
        }
    }
    "it should test recursive question correctly" {
        val (question) = Validator.validate("Recursive Factorial")
            .also { (question, report) ->
                question.validated shouldBe true
                report shouldNotBe null
                report!!.requiredTestCount shouldBeGreaterThan 0
            }
        """
public class Question {
  public static long factorial(long input) {
    return 0;
  }
}
""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also { results ->
                results.complete.testing!!.tests.count { it.passed } shouldBe 0
                results.complete.recursion!!.failed shouldBe true
            }
        }
        """
public class Question {
  public static long factorial(long input) {
    if (input <= 0) {
      throw new IllegalArgumentException();
    }
    long result = 1;
    for (long multiplier = 2; multiplier <= input; multiplier++) {
      result *= multiplier;
    }
    return result;
  }
}
""".trim().also { incorrect ->
            question.test(incorrect, Language.java).also { results ->
                results.failed.checkExecutedSubmission shouldBe null
                results.complete.recursion!!.failed shouldBe true
            }
        }
    }
})