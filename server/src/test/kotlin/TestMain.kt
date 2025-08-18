package edu.illinois.cs.cs125.questioner.server

import com.google.common.truth.Truth.assertThat
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.moshi.moshi
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class TestMain :
    StringSpec({
        "POST route should process add-one question submission with correct solution" {
            testApplication {
                application {
                    questioner(Loader.questions)
                }

                // Get the add-one question for testing
                val question = Loader.getByPath("add-one")
                    ?: error("add-one question should exist for testing")

                // Get the correct solution for Java
                val correctSolution = question.getCorrect(Language.java)
                    ?: error("add-one question should have Java solution")

                // Create a submission JSON manually (simpler than complex Moshi setup)
                val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "${question.published.contentHash}",
  "language": "java",
  "contents": ${moshi.adapter(String::class.java).toJson(correctSolution)}
}
            """.trim()

                // Test the POST route
                val response = client.post("/") {
                    header("content-type", "application/json")
                    setBody(submissionJson)
                }

                // Verify the response
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)

                // Parse the response manually
                val responseText = response.bodyAsText()
                val serverResponse = moshi.adapter(ServerResponse::class.java).fromJson(responseText)!!

                assertThat(serverResponse.type).isEqualTo(Submission.SubmissionType.SOLVE)
                assertThat(serverResponse.solveResults).isNotNull()
                assertThat(serverResponse.solveResults!!.complete).isNotNull()

                // Verify that the correct solution passes
                val testingResults = serverResponse.solveResults!!.complete!!.testing!!
                assertThat(testingResults.passed).isTrue()
                assertThat(testingResults.tests).isNotEmpty()

                // Verify all tests passed
                val passedTests = testingResults.tests.count { it.passed }
                assertThat(passedTests).isEqualTo(testingResults.tests.size)
            }
        }

        "POST route should handle incorrect solution" {
            testApplication {
                application {
                    questioner(Loader.questions)
                }

                // Get the add-one question
                val question = Loader.getByPath("add-one")
                    ?: error("add-one question should exist for testing")

                // Create an incorrect solution (returns wrong value for addOne)
                val incorrectSolution = """
int addOne(int value) {
    return value + 2; // Incorrect - should return value + 1
}
            """.trim()

                // Create a submission JSON with incorrect solution
                val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "${question.published.contentHash}",
  "language": "java",
  "contents": ${moshi.adapter(String::class.java).toJson(incorrectSolution)}
}
            """.trim()

                // Test the POST route
                val response = client.post("/") {
                    header("content-type", "application/json")
                    setBody(submissionJson)
                }

                // Should still return OK but with failed tests
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)

                val responseText = response.bodyAsText()
                val serverResponse = moshi.adapter(ServerResponse::class.java).fromJson(responseText)!!

                assertThat(serverResponse.type).isEqualTo(Submission.SubmissionType.SOLVE)
                assertThat(serverResponse.solveResults).isNotNull()

                // The solution should fail - either in compilation or testing
                val results = serverResponse.solveResults!!
                assertThat(results.complete.testing!!.passed).isFalse()
            }
        }

        "POST route should return 404 for non-existent question" {
            testApplication {
                application {
                    questioner(Loader.questions)
                }

                // Create a submission with invalid contentHash
                val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "invalid-hash-that-does-not-exist",
  "language": "java",
  "contents": "public class Test { }"
}
            """.trim()

                // Test the POST route
                val response = client.post("/") {
                    header("content-type", "application/json")
                    setBody(submissionJson)
                }

                // Should return 404 for non-existent question
                assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
            }
        }
    })
