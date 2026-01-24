package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import edu.illinois.cs.cs125.questioner.lib.test
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString

class TestSimpleIfElseServer :
    StringSpec({

        "Layer 1: Direct question.test() call" {
            val question = Loader.getByPath("simple-if-else")
                ?: error("simple-if-else question should exist")

            val validationResults = question.validationResults!!
            val validationMemory = validationResults.memoryAllocation.java
            val correctSolution = question.getCorrect(Language.java)!!

            val results = question.test(correctSolution, Language.java)

            results.succeeded shouldBe true
            results.complete.memoryAllocation?.failed shouldBe false
        }

        "Layer 2: submission.test(question) call (no HTTP)" {
            val question = Loader.getByPath("simple-if-else")
                ?: error("simple-if-else question should exist")

            val correctSolution = question.getCorrect(Language.java)!!

            val submission = Submission(
                type = Submission.SubmissionType.SOLVE,
                contentHash = question.published.contentHash,
                language = Language.java,
                contents = correctSolution,
            )

            val serverResponse = submission.test(question)
            val results = serverResponse.solveResults!!

            results.succeeded shouldBe true
            results.complete.memoryAllocation?.failed shouldBe false
        }

        "Layer 3: Full HTTP through testApplication" {
            testApplication {
                application {
                    questioner(Loader.questions)
                }

                val question = Loader.getByPath("simple-if-else")
                    ?: error("simple-if-else question should exist")

                val correctSolution = question.getCorrect(Language.java)!!

                val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "${question.published.contentHash}",
  "language": "java",
  "contents": ${json.encodeToString(correctSolution)}
}
            """.trim()

                val response = client.post("/") {
                    header("content-type", "application/json")
                    setBody(submissionJson)
                }

                response.status shouldBe HttpStatusCode.OK

                val responseText = response.bodyAsText()
                val serverResponse = json.decodeFromString<ServerResponse>(responseText)

                val results = serverResponse.solveResults!!
                results.succeeded shouldBe true
                results.complete.memoryAllocation?.failed shouldBe false
            }
        }
    })
