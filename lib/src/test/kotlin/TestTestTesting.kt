package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.compilationCache
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.system.measureTimeMillis

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
        val (question) = Validator.validate("Add One Class").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }
        question.testTests(JAVA_EMPTY_SUITE_CLASS, Language.java).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.also {
                it.total shouldBe 7
                it.correct shouldBe 1
                it.identifiedSolution shouldBe false
                it.shortCircuited shouldBe false
                it.succeeded shouldBe false
            }
        }
        question.testTests(JAVA_EMPTY_SUITE_CLASS, Language.java, Question.TestTestingSettings(true))
            .also { results ->
                results.failedSteps.size shouldBe 0
                results.complete.testTesting!!.also {
                    it.total shouldBe 7
                    it.correct shouldBe 0
                    it.identifiedSolution shouldBe null
                    it.shortCircuited shouldBe true
                    it.succeeded shouldBe false
                }
            }
        question.testTests(KOTLIN_EMPTY_SUITE, Language.kotlin).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.also {
                it.total shouldBe 7
                it.correct shouldBe 1
                it.identifiedSolution shouldBe false
                it.shortCircuited shouldBe false
                it.succeeded shouldBe false
            }
        }
        question.testTests(
            """
public class TestQuestion {
  public static void test() {
    assert(Question.addOne(0) == 1);
    assert(Question.addOne(1) == 2);
  }
}""", Language.java
        ).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.succeeded shouldBe true
        }
        question.testTests(
            """
fun test() {
  check(Question.addOne(0) == 1)
}
""", Language.kotlin
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
""", Language.kotlin
        ).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.succeeded shouldBe true
        }
    }
    "should timeout test test suites for classes" {
        val (question) = Validator.validate("Add One Class").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }
        val sleepTime = measureTimeMillis {
            question.testTests(
                """
public class TestQuestion {
  public static void test() throws InterruptedException {
    Thread.sleep(1000);
  }
}""", Language.java
            ).also { results ->
                results.timeout shouldBe true
                results.completedSteps shouldNotContain TestTestResults.Step.testTesting
            }
        }
        sleepTime shouldBeLessThan question.testTestingLimits!!.timeout.toLong() * 2

        val lineCountTime = measureTimeMillis {
            question.testTests(
                """
public class TestQuestion {
  public static void test() {
    int j = 0;
    for (int i = 0; i < 1024 * 1024; i++) {
      j++;
    }
  }
}""", Language.java
            ).also { results ->
                results.lineCountTimeout shouldBe true
                results.completedSteps shouldNotContain TestTestResults.Step.checkExecutedSubmission
                results.failed.checkExecutedSubmission shouldNotBe null
                results.completedSteps shouldNotContain TestTestResults.Step.testTesting
            }
        }
        lineCountTime shouldBeLessThan question.testTestingLimits!!.timeout.toLong()
    }
    "should fail fields" {
        val (question) = Validator.validate("Add One Class").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }
        question.testTests(
            """
public class TestQuestion {
  private static final int value = 0;
  public static void test() throws InterruptedException {
    Thread.sleep(1000);
  }
}""", Language.java
        ).also { results ->
            results.failedSteps shouldContain TestTestResults.Step.checkCompiledSubmission
        }
    }
    "should collect output" {
        val (question) = Validator.validate("Add One Class").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }
        question.testTests(JAVA_EMPTY_SUITE_CLASS, Language.java).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.also {
                it.total shouldBe 7
                it.shortCircuited shouldBe false
                it.succeeded shouldBe false
            }
        }
        question.testTests(
            """
public class TestQuestion {
  public static void test() throws InterruptedException {
    System.out.println("Here");
  }
}""", Language.java
        ).also { results ->
            results.completedSteps shouldContain TestTestResults.Step.testTesting
            results.complete.testTesting!!.also { testTestingResults ->
                testTestingResults.output shouldHaveSize testTestingResults.total
                testTestingResults.output.all { it.trim() == "Here" }
            }
        }
    }
    "should test test suites for methods" {
        val (question) = Validator.validate("Add One").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }
        question.testTests(JAVA_EMPTY_SUITE_METHOD, Language.java).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.also {
                it.total shouldBe question.testTestingIncorrect!!.size + 1
                it.shortCircuited shouldBe false
                it.succeeded shouldBe false
            }
        }
        question.testTests(KOTLIN_EMPTY_SUITE, Language.kotlin).also { results ->
            results.failedSteps.size shouldBe 0
            results.complete.testTesting!!.also {
                it.total shouldBe question.testTestingIncorrect!!.size + 1
                it.shortCircuited shouldBe false
                it.succeeded shouldBe false
            }
        }
        question.testTests(
            """void test() {
  assert(addOne(0) == 0);
}""", Language.java
        ).also { results ->
            results.failedSteps.size shouldBe 0
        }
        question.testTests(
            """fun test() {
  require(addOne(0) == 0)
}""", Language.kotlin
        ).also { results ->
            results.failedSteps.size shouldBe 0
        }
    }
    "original incorrect examples should recover and fail" {
        val (question) = Validator.validate("Add One Class").also { (question, report) ->
            question.validated shouldBe true
            report shouldNotBe null
        }

        compilationCache.invalidateAll()
        val compileTime = measureTimeMillis {
            question.compileAllTestTestingIncorrect()
        }
        val recompileTime = measureTimeMillis {
            question.compileAllTestTestingIncorrect()
        }
        recompileTime * 4 shouldBeLessThan compileTime

        question.testTestingIncorrect shouldNotBe null
        question.testTestingIncorrect!!.size shouldBeGreaterThan 0
        question.testTestingIncorrect!!.forEach { incorrect ->
            incorrect.language shouldBe Language.java
            incorrect.compiled(question).also {
                (it.classloader as CopyableClassLoader).bytecodeForClasses.keys shouldContain question.published.klass
            }
            question.test(incorrect.contents(question), incorrect.language).also {
                it.complete.testing?.passed shouldBe false
                it.tests()?.size shouldBe incorrect.testCount + 1
            }
        }
    }
})