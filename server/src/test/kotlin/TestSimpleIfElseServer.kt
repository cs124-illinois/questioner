package edu.illinois.cs.cs125.questioner.server

import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.TestResults
import edu.illinois.cs.cs125.questioner.lib.serialization.json
import edu.illinois.cs.cs125.questioner.lib.server.Submission
import edu.illinois.cs.cs125.questioner.lib.test
import edu.illinois.cs.cs125.questioner.lib.warm
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

    // Helper to print memory results
    fun printMemoryResults(label: String, results: TestResults, validationMemory: Long) {
        println("=== $label ===")
        results.complete.memoryAllocation?.let { memAlloc ->
            println("Memory Allocation:")
            println("  Solution (validation): ${memAlloc.solution}")
            println("  Submission (this run): ${memAlloc.submission}")
            println("  Limit:                 ${memAlloc.limit}")
            println("  Increase:              ${memAlloc.increase}")
            println("  Failed:                ${memAlloc.failed}")
        }
        results.complete.memoryBreakdown?.let { breakdown ->
            println("Memory Breakdown:")
            println("  heapAllocatedMemory: ${breakdown.heapAllocatedMemory}")
            println("  maxCallStackSize:    ${breakdown.maxCallStackSize}")
            println("  warmupMemory:        ${breakdown.warmupMemory}")
            println("  warmupCount:         ${breakdown.warmupCount}")
        }
        println()
    }

    "Layer 0: Multiple question.test() calls to check warmup effect" {
        val question = Loader.getByPath("simple-if-else")
            ?: error("simple-if-else question should exist")

        val validationResults = question.validationResults!!
        val validationMemory = validationResults.memoryAllocation.java
        val correctSolution = question.getCorrect(Language.java)!!

        println("Validation memoryAllocation (Java): $validationMemory")
        println()

        // Run test multiple times to see if memory decreases
        println("Running question.test() multiple times:")
        for (i in 1..5) {
            val results = question.test(correctSolution, Language.java)
            val memory = results.complete.memoryAllocation?.submission ?: -1
            println("  Run $i: heapAllocatedMemory=${results.complete.memoryBreakdown?.heapAllocatedMemory}, submission=$memory")
        }
        println()

        // Final run for detailed output
        val results = question.test(correctSolution, Language.java)
        printMemoryResults("Layer 0: After 5 warmup runs", results, validationMemory)

        results.complete.memoryAllocation?.let {
            println("RESULT: After warmup submission=${it.submission}, expected=$validationMemory, match=${it.submission == validationMemory}")
        }
    }

    "Layer 1: Direct question.test() call WITHOUT warm()" {
        val question = Loader.getByPath("simple-if-else")
            ?: error("simple-if-else question should exist")

        val validationResults = question.validationResults!!
        val validationMemory = validationResults.memoryAllocation.java

        println("Validation memoryAllocation (Java): $validationMemory")
        println()

        val correctSolution = question.getCorrect(Language.java)!!

        // Direct call WITHOUT warming
        val results = question.test(correctSolution, Language.java)

        printMemoryResults("Layer 1: Direct question.test() WITHOUT warm()", results, validationMemory)

        results.complete.memoryAllocation?.let {
            println("RESULT: Without warm() submission=${it.submission}, expected=$validationMemory, match=${it.submission == validationMemory}")
        }
    }

    "Layer 2: submission.test(question) call (no HTTP)" {
        val question = Loader.getByPath("simple-if-else")
            ?: error("simple-if-else question should exist")

        val validationResults = question.validationResults!!
        val validationMemory = validationResults.memoryAllocation.java

        println("Validation memoryAllocation (Java): $validationMemory")
        println()

        val correctSolution = question.getCorrect(Language.java)!!

        // Create submission object directly
        val submission = Submission(
            type = Submission.SubmissionType.SOLVE,
            contentHash = question.published.contentHash,
            language = Language.java,
            contents = correctSolution
        )

        // Call through submission.test() - this is what the server does
        val serverResponse = submission.test(question)

        val results = serverResponse.solveResults!!
        printMemoryResults("Layer 2: submission.test(question)", results, validationMemory)

        results.complete.memoryAllocation?.let {
            println("RESULT: submission.test() submission=${it.submission}, expected=$validationMemory, match=${it.submission == validationMemory}")
        }
    }

    "Layer 3: Full HTTP through testApplication" {
        testApplication {
            application {
                questioner(Loader.questions)
            }

            val question = Loader.getByPath("simple-if-else")
                ?: error("simple-if-else question should exist")

            val validationResults = question.validationResults!!
            val validationMemory = validationResults.memoryAllocation.java

            println("Validation memoryAllocation (Java): $validationMemory")
            println()

            val correctSolution = question.getCorrect(Language.java)!!

            // Create submission JSON
            val submissionJson = """
{
  "type": "SOLVE",
  "contentHash": "${question.published.contentHash}",
  "language": "java",
  "contents": ${json.encodeToString(correctSolution)}
}
            """.trim()

            // Full HTTP request
            val response = client.post("/") {
                header("content-type", "application/json")
                setBody(submissionJson)
            }

            response.status shouldBe HttpStatusCode.OK

            val responseText = response.bodyAsText()
            val serverResponse = json.decodeFromString<ServerResponse>(responseText)

            val results = serverResponse.solveResults!!
            printMemoryResults("Layer 3: Full HTTP testApplication", results, validationMemory)

            results.complete.memoryAllocation?.let {
                println("RESULT: HTTP submission=${it.submission}, expected=$validationMemory, match=${it.submission == validationMemory}")
            }
        }
    }

    "Summary: Compare all layers" {
        val question = Loader.getByPath("simple-if-else")
            ?: error("simple-if-else question should exist")

        val validationResults = question.validationResults!!
        val validationMemory = validationResults.memoryAllocation.java
        val correctSolution = question.getCorrect(Language.java)!!

        println("=== Memory Allocation Summary ===")
        println("Validation: $validationMemory bytes")
        println()

        // Layer 1: Direct
        val directResults = question.test(correctSolution, Language.java)
        val directMemory = directResults.complete.memoryAllocation?.submission ?: -1

        // Layer 2: submission.test()
        val submission = Submission(
            type = Submission.SubmissionType.SOLVE,
            contentHash = question.published.contentHash,
            language = Language.java,
            contents = correctSolution
        )
        val submissionResponse = submission.test(question)
        val submissionMemory = submissionResponse.solveResults?.complete?.memoryAllocation?.submission ?: -1

        // Layer 3: HTTP (run inside testApplication)
        var httpMemory = -1L
        testApplication {
            application {
                questioner(Loader.questions)
            }

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

            val serverResponse = json.decodeFromString<ServerResponse>(response.bodyAsText())
            httpMemory = serverResponse.solveResults?.complete?.memoryAllocation?.submission ?: -1
        }

        println("Layer 1 (direct question.test):    $directMemory bytes")
        println("Layer 2 (submission.test):         $submissionMemory bytes")
        println("Layer 3 (HTTP testApplication):    $httpMemory bytes")
        println()
        println("Differences from validation ($validationMemory):")
        println("  Layer 1: ${directMemory - validationMemory}")
        println("  Layer 2: ${submissionMemory - validationMemory}")
        println("  Layer 3: ${httpMemory - validationMemory}")
    }
})
