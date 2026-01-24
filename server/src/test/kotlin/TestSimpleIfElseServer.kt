package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString

class TestSimpleIfElseServer : StringSpec({
    "should test Simple If Else through server and compare memory" {
        testApplication {
            application {
                questioner(Loader.questions)
            }

            val question = Loader.getByPath("simple-if-else")
                ?: error("simple-if-else question should exist")

            val validationResults = question.validationResults
            validationResults shouldNotBe null

            println("=== Server Test: Simple If Else ===")
            println()
            println("Validation memoryAllocation (Java): ${validationResults!!.memoryAllocation.java}")
            println()

            // Get the correct solution
            val correctSolution = question.getCorrect(Language.java)
                ?: error("simple-if-else should have Java solution")

            println("Solution code:")
            println(correctSolution)
            println()

            // Create submission
            val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "${question.published.contentHash}",
  "language": "java",
  "contents": ${json.encodeToString(correctSolution)}
}
            """.trim()

            // Test through server
            val response = client.post("/") {
                header("content-type", "application/json")
                setBody(submissionJson)
            }

            response.status shouldBe HttpStatusCode.OK

            val responseText = response.bodyAsText()
            val serverResponse = json.decodeFromString<ServerResponse>(responseText)

            serverResponse.type shouldBe Submission.SubmissionType.SOLVE
            serverResponse.solveResults shouldNotBe null

            val results = serverResponse.solveResults!!
            println("Server Response:")
            println("  Succeeded: ${results.succeeded}")
            println("  Timeout: ${results.timeout}")
            println("  Failed Steps: ${results.failedSteps}")
            println()

            results.complete.memoryAllocation?.let { memAlloc ->
                println("Memory Allocation Comparison (via Server):")
                println("  Solution (from validation): ${memAlloc.solution}")
                println("  Submission (this run):      ${memAlloc.submission}")
                println("  Limit:                      ${memAlloc.limit}")
                println("  Increase:                   ${memAlloc.increase}")
                println("  Failed:                     ${memAlloc.failed}")
                println()
            }

            results.complete.executionCount?.let { execCount ->
                println("Execution Count Comparison (via Server):")
                println("  Solution (from validation): ${execCount.solution}")
                println("  Submission (this run):      ${execCount.submission}")
                println("  Limit:                      ${execCount.limit}")
                println("  Increase:                   ${execCount.increase}")
                println("  Failed:                     ${execCount.failed}")
                println()
            }

            results.complete.memoryBreakdown?.let { breakdown ->
                println("Submission Memory Breakdown (via Server):")
                println("  heapAllocatedMemory: ${breakdown.heapAllocatedMemory}")
                println("  maxCallStackSize:    ${breakdown.maxCallStackSize}")
                println("  warmupMemory:        ${breakdown.warmupMemory}")
                println("  warmupCount:         ${breakdown.warmupCount}")
                println("  totalWithStack:      ${breakdown.totalWithStack}")
                println("  totalWithWarmup:     ${breakdown.totalWithWarmup}")
                println()
            }

            results.complete.submissionAllocationRecords?.let { records ->
                println("Submission Allocation Records (${records.size} total):")
                records.take(20).forEachIndexed { i, record ->
                    println("  [$i] bytes=${record.bytes}, caller=${record.callerClass}")
                }
                if (records.size > 20) {
                    println("  ... and ${records.size - 20} more")
                }
                println()
            }

            println("=== Comparison Summary ===")
            println("Validation memoryAllocation (Java): ${validationResults.memoryAllocation.java}")
            results.complete.memoryAllocation?.let {
                println("Server submission memory:           ${it.submission}")
                println("Difference:                         ${it.increase}")
                println("Match: ${it.increase == 0L}")
            }
        }
    }
})
