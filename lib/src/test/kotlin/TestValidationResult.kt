package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.serialization.json
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class TestValidationResult : StringSpec({
    "ValidationResult.Success should serialize and deserialize" {
        val original = ValidationResult.Success(
            questionPath = "/test/path/question.parsed.json",
            questionName = "Test Question",
            questionAuthor = "test@test.com",
            questionSlug = "test/question",
            phase = ValidationPhase.VALIDATE,
            timestamp = System.currentTimeMillis(),
            durationMs = 1234,
            summary = SuccessSummary(
                seed = 42,
                retries = 2,
                testCount = 50,
                requiredTestCount = 10,
                requiredTime = 500,
                mutationCount = 15,
                hasKotlin = true,
                testingSequence = listOf("step1", "step2", "step3"),
            ),
        )

        val jsonString = json.encodeToString(ValidationResult.serializer(), original)
        jsonString shouldContain "\"success\""
        jsonString shouldContain "Test Question"

        val deserialized = json.decodeFromString(ValidationResult.serializer(), jsonString)
        deserialized shouldBe original
    }

    "ValidationResult.Failure should serialize and deserialize" {
        val original = ValidationResult.Failure(
            questionPath = "/test/path/question.parsed.json",
            questionName = "Test Question",
            questionAuthor = "test@test.com",
            questionSlug = "test/question",
            phase = ValidationPhase.CALIBRATE,
            timestamp = System.currentTimeMillis(),
            durationMs = 5678,
            error = ValidationError.SolutionFailed(
                solutionCode = "public int test() { return 42; }",
                solutionPath = "/test/Solution.java",
                solutionLanguage = "java",
                explanation = "Test failed with wrong output",
                retries = 3,
                testingSequence = listOf("testing step"),
            ),
        )

        val jsonString = json.encodeToString(ValidationResult.serializer(), original)
        jsonString shouldContain "\"failure\""
        jsonString shouldContain "solution_failed"

        val deserialized = json.decodeFromString(ValidationResult.serializer(), jsonString)
        deserialized shouldBe original
    }

    "ValidationError.IncorrectPassed should serialize with mutation info" {
        val error = ValidationError.IncorrectPassed(
            incorrectCode = "public int test() { return 0; }",
            incorrectPath = "/test/Incorrect.java",
            incorrectLanguage = "java",
            correctCode = "public int test() { return 42; }",
            correctPath = "/test/Correct.java",
            isMutation = true,
            mutationType = "NEGATE_CONDITIONALS",
            suppressionComment = "// mutate-disable-negate-conditionals",
            testingSequence = null,
        )

        val jsonString = json.encodeToString(ValidationError.serializer(), error)
        jsonString shouldContain "incorrect_passed"
        jsonString shouldContain "NEGATE_CONDITIONALS"

        val deserialized = json.decodeFromString(ValidationError.serializer(), jsonString)
        deserialized shouldBe error
    }

    "ValidationError.UnexpectedError should serialize" {
        val error = ValidationError.UnexpectedError(
            exceptionType = "NullPointerException",
            stackTrace = "at Test.main(Test.java:10)",
            testingSequence = listOf("step1"),
            message = "Unexpected null value",
        )

        val jsonString = json.encodeToString(ValidationError.serializer(), error)
        jsonString shouldContain "unexpected_error"
        jsonString shouldContain "NullPointerException"

        val deserialized = json.decodeFromString(ValidationError.serializer(), jsonString)
        deserialized shouldBe error
    }

    "ValidationFailed.toValidationError should preserve all fields" {
        val solution = Question.FlatFile(
            klass = "Test",
            contents = "public int test() { return 42; }",
            language = Language.java,
            path = "/test/Solution.java",
        )
        val failed = SolutionFailed(
            solution = solution,
            explanation = "Test explanation",
            retries = 2,
            testingSequence = listOf("step1", "step2"),
        )

        val error = failed.toValidationError()
        error.errorType shouldBe "SolutionFailed"
        error.testingSequence shouldBe listOf("step1", "step2")
        (error as ValidationError.SolutionFailed).solutionCode shouldBe solution.contents
        error.solutionPath shouldBe solution.path
        error.retries shouldBe 2
    }

    "All ValidationError types should have correct errorType" {
        ValidationError.SolutionFailed("", null, "java", "", 0, null).errorType shouldBe "SolutionFailed"
        ValidationError.SolutionReceiverGeneration("", null, "java", 0, null).errorType shouldBe "SolutionReceiverGeneration"
        ValidationError.SolutionFailedLinting("", null, "java", "", null).errorType shouldBe "SolutionFailedLinting"
        ValidationError.SolutionThrew("", null, "java", "", "", null).errorType shouldBe "SolutionThrew"
        ValidationError.SolutionTestingThrew("", null, "java", "", "", "", null).errorType shouldBe "SolutionTestingThrew"
        ValidationError.SolutionLacksEntropy("", null, "java", 0, 0, "", false, null, null).errorType shouldBe "SolutionLacksEntropy"
        ValidationError.SolutionDeadCode("", null, "java", 0, 0, listOf(), null).errorType shouldBe "SolutionDeadCode"
        ValidationError.NoIncorrect("", null, "java").errorType shouldBe "NoIncorrect"
        ValidationError.TooFewMutations("", null, "java", 0, 0).errorType shouldBe "TooFewMutations"
        ValidationError.TooMuchOutput("", null, "java", 0, 0, null).errorType shouldBe "TooMuchOutput"
        ValidationError.IncorrectFailedLinting("", null, "java", "", null, "", null).errorType shouldBe "IncorrectFailedLinting"
        ValidationError.IncorrectPassed("", null, "java", "", null, false, null, null, null).errorType shouldBe "IncorrectPassed"
        ValidationError.IncorrectTooManyTests("", null, "java", "", null, 0, 0, null, false, null, null, null).errorType shouldBe "IncorrectTooManyTests"
        ValidationError.IncorrectWrongReason("", null, "java", "", "", null).errorType shouldBe "IncorrectWrongReason"
        ValidationError.IncorrectTestingThrew("", null, "java", "", "", "", null).errorType shouldBe "IncorrectTestingThrew"
        ValidationError.UnexpectedError("", "", null, "").errorType shouldBe "UnexpectedError"
    }

    "ValidationResultFormatter should generate HTML for success" {
        val result = ValidationResult.Success(
            questionPath = "/test/path/question.parsed.json",
            questionName = "Test Question",
            questionAuthor = "test@test.com",
            questionSlug = "test/question",
            phase = ValidationPhase.VALIDATE,
            timestamp = System.currentTimeMillis(),
            durationMs = 1234,
            summary = SuccessSummary(
                seed = 42,
                retries = 2,
                testCount = 50,
                requiredTestCount = 10,
                requiredTime = 500,
                mutationCount = 15,
                hasKotlin = true,
                testingSequence = null,
            ),
        )

        val html = ValidationResultFormatter.formatHtml(result)
        html shouldContain "<!DOCTYPE html>"
        html shouldContain "Test Question"
        html shouldContain "Passed"
        html shouldContain "50" // testCount
    }

    "ValidationResultFormatter should generate HTML for failure" {
        val result = ValidationResult.Failure(
            questionPath = "/test/path/question.parsed.json",
            questionName = "Test Question",
            questionAuthor = "test@test.com",
            questionSlug = "test/question",
            phase = ValidationPhase.VALIDATE,
            timestamp = System.currentTimeMillis(),
            durationMs = 1234,
            error = ValidationError.SolutionFailed(
                solutionCode = "public int test() { return 42; }",
                solutionPath = "/test/Solution.java",
                solutionLanguage = "java",
                explanation = "Test failed with wrong output",
                retries = 3,
                testingSequence = null,
            ),
        )

        val html = ValidationResultFormatter.formatHtml(result)
        html shouldContain "<!DOCTYPE html>"
        html shouldContain "Test Question"
        html shouldContain "Failed"
        html shouldContain "SolutionFailed"
        html shouldContain "public int test()"
    }

    "ValidationResultFormatter should generate summary HTML" {
        val results = listOf(
            ValidationResult.Success(
                questionPath = "/test/path/q1.parsed.json",
                questionName = "Question 1",
                questionAuthor = "author1@test.com",
                questionSlug = "author1/question1",
                phase = ValidationPhase.VALIDATE,
                timestamp = System.currentTimeMillis(),
                durationMs = 100,
                summary = SuccessSummary(42, 0, 10, 10, 100, 5, false, null),
            ),
            ValidationResult.Failure(
                questionPath = "/test/path/q2.parsed.json",
                questionName = "Question 2",
                questionAuthor = "author2@test.com",
                questionSlug = "author2/question2",
                phase = ValidationPhase.CALIBRATE,
                timestamp = System.currentTimeMillis(),
                durationMs = 200,
                error = ValidationError.NoIncorrect("", null, "java"),
            ),
        )

        val html = ValidationResultFormatter.formatSummaryHtml(results)
        html shouldContain "<!DOCTYPE html>"
        html shouldContain "Question 1"
        html shouldContain "Question 2"
        html shouldContain "1" // passed count
        html shouldContain "50%" // pass rate
    }

    "ValidationPhase enum should serialize correctly" {
        val jsonValidate = json.encodeToString(ValidationPhase.serializer(), ValidationPhase.VALIDATE)
        jsonValidate shouldBe "\"validate\""

        val jsonCalibrate = json.encodeToString(ValidationPhase.serializer(), ValidationPhase.CALIBRATE)
        jsonCalibrate shouldBe "\"calibrate\""

        json.decodeFromString(ValidationPhase.serializer(), "\"validate\"") shouldBe ValidationPhase.VALIDATE
        json.decodeFromString(ValidationPhase.serializer(), "\"calibrate\"") shouldBe ValidationPhase.CALIBRATE
    }
})
