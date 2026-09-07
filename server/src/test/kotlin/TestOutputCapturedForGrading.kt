package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.serialization.json
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

/**
 * Grading of an output-based question has to actually compare output.
 *
 * Every other HTTP test submits a *correct* solution, which cannot detect a broken capture: if the
 * sandbox captures nothing, the submission's empty output matches the solution's empty output and
 * the submission is accepted. That is not hypothetical. ktor 3.5's `CallLogging` installed jansi,
 * which replaced `System.out` with a stream writing straight to the file descriptor without
 * forwarding, so Jeed captured nothing on the HTTP path and every printing submission would have
 * been marked correct. The whole suite stayed green throughout.
 *
 * This test submits a deliberately wrong printing solution over HTTP and requires it to be
 * rejected, which fails if capture is silently lost regardless of the cause.
 */
class TestOutputCapturedForGrading :
    StringSpec({
        "an incorrect printing submission must be rejected over HTTP" {
            val question = Loader.getByPath("simple-if-else")
                ?: error("simple-if-else question should exist")
            val correct = question.getCorrect(Language.java)!!

            // Always prints "positive", so it must fail on the negative fixed parameters
            // (-10, -1, 0) as long as printed output is really being compared.
            val wrong = "void checkValue(int value) {\n  System.out.println(\"positive\");\n}"

            // Run once outside HTTP first: the capture only broke on executions after the first,
            // so a cold HTTP call would have missed it.
            question.test(correct, Language.java).succeeded shouldBe true

            testApplication {
                application { questioner(Loader.questions) }

                suspend fun submit(contents: String): Boolean {
                    val response = client.post("/") {
                        header("content-type", "application/json")
                        setBody(
                            """
{"type":"SOLVE","contentHash":"${question.published.contentHash}","language":"java","contents":${
                                json.encodeToString(contents)
                            }}
                            """.trim(),
                        )
                    }
                    response.status shouldBe HttpStatusCode.OK
                    return json.decodeFromString<ServerResponse>(response.bodyAsText())
                        .solveResults!!.succeeded
                }

                submit(correct) shouldBe true
                submit(wrong) shouldBe false
            }
        }
    })
