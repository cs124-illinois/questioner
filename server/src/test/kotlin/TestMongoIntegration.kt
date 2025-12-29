package edu.illinois.cs.cs125.questioner.server

import com.google.common.truth.Truth.assertThat
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import org.bson.BsonDocument
import org.testcontainers.mongodb.MongoDBContainer

class TestMongoIntegration :
    StringSpec({
        val mongoContainer = MongoDBContainer("mongo:8.2.3")

        beforeSpec {
            mongoContainer.start()
        }

        afterSpec {
            mongoContainer.stop()
        }

        "should connect to MongoDB 8.2.3 and load questions by path" {
            val connectionString = "${mongoContainer.connectionString}/questioner"
            val collection = createMongoCollection(connectionString, "questions")

            // Load test questions into MongoDB
            val questions = Loader.questions.values
            questions.forEach { question ->
                val bsonDoc = BsonDocument.parse(json.encodeToString(question))
                bsonDoc.append("latest", org.bson.BsonBoolean(true))
                collection.insertOne(bsonDoc)
            }

            // Test getQuestionByPath with real MongoDB
            val question = getQuestionByPath("add-one", collection)
            question.shouldNotBeNull()
            question.published.path shouldBe "add-one"
        }

        "should return null for non-existent question path" {
            val connectionString = "${mongoContainer.connectionString}/questioner"
            val collection = createMongoCollection(connectionString, "questions_empty")

            val question = getQuestionByPath("non-existent-path", collection)
            question shouldBe null
        }

        "should load question by contentHash via Submission.getQuestion" {
            val connectionString = "${mongoContainer.connectionString}/questioner"
            val collection = createMongoCollection(connectionString, "questions_submission")

            // Load test questions into MongoDB
            val testQuestion = Loader.questions.values.first()
            val bsonDoc = BsonDocument.parse(json.encodeToString(testQuestion))
            bsonDoc.append("latest", org.bson.BsonBoolean(true))
            collection.insertOne(bsonDoc)

            // Clear the cache to ensure we hit MongoDB
            questionCache.invalidateAll()

            // Create a submission with the question's contentHash
            val submission = Submission(
                type = Submission.SubmissionType.SOLVE,
                contentHash = testQuestion.published.contentHash,
                language = Language.java,
                contents = "// test",
            )

            // Test getQuestion with real MongoDB collection
            val question = submission.getQuestion(collection = collection)
            question.shouldNotBeNull()
            question.published.contentHash shouldBe testQuestion.published.contentHash
        }

        "should handle full POST flow with real MongoDB" {
            val connectionString = "${mongoContainer.connectionString}/questioner"
            val collection = createMongoCollection(connectionString, "questions_e2e")

            // Load test question into MongoDB
            val testQuestion = Loader.getByPath("add-one")
                ?: error("add-one question should exist for testing")
            val bsonDoc = BsonDocument.parse(json.encodeToString(testQuestion))
            bsonDoc.append("latest", org.bson.BsonBoolean(true))
            collection.insertOne(bsonDoc)

            // Clear the cache to ensure we hit MongoDB
            questionCache.invalidateAll()

            // Get the correct solution for Java
            val correctSolution = testQuestion.getCorrect(Language.java)
                ?: error("add-one question should have Java solution")

            testApplication {
                application {
                    // Pass testingCollection but NOT testingQuestions to force MongoDB path
                    questioner(testingCollection = collection)
                }

                // Create a submission JSON
                val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "${testQuestion.published.contentHash}",
  "language": "java",
  "contents": ${json.encodeToString(correctSolution)}
}
                """.trim()

                // Test the POST route with real MongoDB
                val response = client.post("/") {
                    header("content-type", "application/json")
                    setBody(submissionJson)
                }

                // Verify the response
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)

                val responseText = response.bodyAsText()
                val serverResponse = json.decodeFromString<ServerResponse>(responseText)

                assertThat(serverResponse.type).isEqualTo(Submission.SubmissionType.SOLVE)
                assertThat(serverResponse.solveResults).isNotNull()
                assertThat(serverResponse.solveResults!!.complete).isNotNull()

                // Verify that the correct solution passes
                val testingResults = serverResponse.solveResults.complete.testing!!
                assertThat(testingResults.passed).isTrue()
            }
        }

        "should return 404 for non-existent question with real MongoDB" {
            val connectionString = "${mongoContainer.connectionString}/questioner"
            val collection = createMongoCollection(connectionString, "questions_404")

            // Empty collection - no questions loaded
            questionCache.invalidateAll()

            testApplication {
                application {
                    questioner(testingCollection = collection)
                }

                val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "non-existent-hash",
  "language": "java",
  "contents": "public class Test { }"
}
                """.trim()

                val response = client.post("/") {
                    header("content-type", "application/json")
                    setBody(submissionJson)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
            }
        }
    })
