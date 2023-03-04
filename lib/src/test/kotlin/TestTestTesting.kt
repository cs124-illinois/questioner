package edu.illinois.cs.cs125.questioner.lib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Path
import kotlin.system.measureTimeMillis

private val validator = Validator(
    Path.of(object {}::class.java.getResource("/questions.json")!!.toURI()).toFile(),
    "",
    seed = 124,
    maxMutationCount = 64
)

const val JAVA_EMPTY_SUITE_CLASS = """
public class TestQuestion {
  public static void test() {
  }
}
"""
const val JAVA_EMPTY_SUITE_METHOD = """void test() {
}
"""
const val KOTLIN_EMPTY_SUITE = """fun test() {
}
"""

class TestTestTesting : StringSpec({
    "should test test suites for classes" {
        val (question) = validator.validate("Add One Class", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }
        question.testTests(JAVA_EMPTY_SUITE_CLASS, Question.Language.java).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.succeeded shouldBe false
        }
        question.testTests(KOTLIN_EMPTY_SUITE, Question.Language.kotlin).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.succeeded shouldBe false
        }
        question.testTests(
            """
public class TestQuestion {
  public static void test() {
    assert(Question.addOne(0) == 1);
    assert(Question.addOne(1) == 2);
  }
}""", Question.Language.java
        ).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.succeeded shouldBe true
        }
        question.testTests(
            """
fun test() {
  check(Question.addOne(0) == 1)
}
""", Question.Language.kotlin
        ).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.succeeded shouldBe false
            results.complete.testTesting!!.incorrect shouldBe 1
        }
        question.testTests(
            """
fun test() {
  check(Question.addOne(0) == 1)
  check(Question.addOne(1) == 2)
}
""", Question.Language.kotlin
        ).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.succeeded shouldBe true
        }
    }
    "should test test suites for methods" {
        val (question) = validator.validate("Add One", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }
        question.testTests(JAVA_EMPTY_SUITE_METHOD, Question.Language.java).also { results ->
            results.failedSteps.size shouldBe 0
        }
        question.testTests(KOTLIN_EMPTY_SUITE, Question.Language.kotlin).also { results ->
            results.failedSteps.size shouldBe 0
        }
        question.testTests(
            """void test() {
  assert(addOne(0) == 0);
}""", Question.Language.java
        ).also { results ->
            results.failedSteps.size shouldBe 0
        }
        question.testTests(
            """fun test() {
  require(addOne(0) == 0)
}""", Question.Language.kotlin
        ).also { results ->
            results.failedSteps.size shouldBe 0
        }
    }
    "original incorrect examples should recover and fail" {
        val (question) = validator.validate("Add One Class", force = true, testing = true).also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }

        val compileTime = measureTimeMillis {
            question.compileAllValidationMutations()
        }
        val recompileTime = measureTimeMillis {
            question.compileAllValidationMutations()
        }
        recompileTime * 10 shouldBeLessThan compileTime

        question.validationMutations shouldNotBe null
        question.validationMutations!!.size shouldBeGreaterThan 0
        question.validationMutations!!.forEach { incorrect ->
            incorrect.language shouldBe Question.Language.java
            incorrect.compiled(question).also {
                (it.classloader as CopyableClassLoader).bytecodeForClasses.keys shouldContain question.klass
            }
            question.test(incorrect.contents(question), incorrect.language).also {
                it.complete.testing?.passed shouldBe false
                it.tests()?.size shouldBe incorrect.testCount + 1
            }
        }
    }
})