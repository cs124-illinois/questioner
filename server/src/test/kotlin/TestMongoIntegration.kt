package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.questioner.lib.serialization.json
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
    })
