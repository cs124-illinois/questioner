package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.jeed.core.useCompilationCache
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Test testing compiles every mutant of the solution through the same path as a student
 * submission, then rewrites the compiled class so its methods are public and it is not final. With
 * Jeed's compilation cache on, as in production, a student submission identical to a mutant shares
 * that mutant's cache entry. The rewrite once landed in the cached entry itself, so after any test
 * testing submission on a question, a student who submitted the same code as one of its mutants got
 * "Your submission is missing a method" instead of their test results.
 */
class TestTestTestingCacheIsolation :
    StringSpec({
        val question = Loader.getByPath("add-one") ?: error("add-one question should exist")

        // Each is exactly one of add-one's test testing mutants.
        val incorrect = mapOf(
            Language.java to "int addOne(int value) {\n  return value + 2;\n}",
            Language.kotlin to "fun addOne(value: Int): Int = value",
        )
        val emptySuite = mapOf(
            Language.java to "void test() {\n}",
            Language.kotlin to "fun test() {\n}",
        )

        "the server tests run with Jeed's compilation cache on, as production does" {
            useCompilationCache shouldBe true
        }

        for (language in listOf(Language.java, Language.kotlin)) {
            "$language: test testing should not change how a matching incorrect submission is graded" {
                suspend fun gradeIncorrect() = Submission(
                    Submission.SubmissionType.SOLVE,
                    question.published.contentHash,
                    language,
                    incorrect.getValue(language),
                ).test(question).solveResults!!

                val before = gradeIncorrect()
                before.failedSteps.shouldBeEmpty()
                before.complete.testing!!.passed shouldBe false

                Submission(
                    Submission.SubmissionType.TESTTESTING,
                    question.published.contentHash,
                    language,
                    emptySuite.getValue(language),
                ).test(question).testTestingResults!!.completed shouldBe true

                val after = gradeIncorrect()
                after.failed.checkExecutedSubmission shouldBe null
                after.failedSteps.shouldBeEmpty()
                after.complete.testing!!.passed shouldBe false
            }
        }
    })
