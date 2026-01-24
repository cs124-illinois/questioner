package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.serialization.json
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

// Direct loader like server's Loader - no validation, no warm()
object DirectLoader {
    val questions =
        json.decodeFromString<List<Question>>(object {}::class.java.getResource("/questions.json")!!.readText())
            .associateBy { question -> question.published.path }

    fun getByPath(path: String) = questions[path]
}

class TestSimpleIfElse : StringSpec({
    "should test with DirectLoader (like server) to compare" {
        // Force deserialization by loading fresh
        val freshQuestions = json.decodeFromString<List<Question>>(
            object {}::class.java.getResource("/questions.json")!!.readText()
        ).associateBy { it.published.path }

        val question = freshQuestions["simple-if-else"]
            ?: error("simple-if-else question should exist")

        val validationResults = question.validationResults!!
        val validationMemory = validationResults.memoryAllocation.java
        val correctSolution = question.getCorrect(Language.java)!!

        println("=== Lib Test with fresh deserialization (NO validate) ===")
        println("Validation memoryAllocation (Java): $validationMemory")
        println()

        // Run test multiple times WITHOUT calling validate()
        println("Running question.test() multiple times (no validate):")
        for (i in 1..5) {
            val results = question.test(correctSolution, Language.java)
            val memory = results.complete.memoryAllocation?.submission ?: -1
            println("  Run $i: heapAllocatedMemory=${results.complete.memoryBreakdown?.heapAllocatedMemory}, submission=$memory")
        }
    }

    "should check if validate() changes the stored memoryAllocation" {
        // Force deserialization by loading fresh
        val freshQuestions = json.decodeFromString<List<Question>>(
            object {}::class.java.getResource("/questions.json")!!.readText()
        ).associateBy { it.published.path }

        val question = freshQuestions["simple-if-else"]
            ?: error("simple-if-else question should exist")

        println("=== Checking if validate() updates memoryAllocation ===")
        println("From JSON - memoryAllocation.java: ${question.validationResults!!.memoryAllocation.java}")

        // Call validate() - this should re-calculate memoryAllocation
        question.warm()
        question.validate(124, 64)

        println("After validate() - memoryAllocation.java: ${question.validationResults!!.memoryAllocation.java}")
        println()
        println("If these differ, validate() is updating the baseline to match current JVM measurements")
    }

    "should test using Validator questions map directly" {
        // Use the SAME question object that Validator uses (already deserialized at class load time)
        val (question, _) = Validator.validate("Simple If Else")

        val validationMemory = question.validationResults!!.memoryAllocation.java
        val correctSolution = question.getCorrect(Language.java)!!

        println("=== Lib Test with Validator's pre-loaded question ===")
        println("Validation memoryAllocation (Java): $validationMemory")
        println()

        // Run test multiple times
        println("Running question.test() multiple times:")
        for (i in 1..5) {
            val results = question.test(correctSolution, Language.java)
            val memory = results.complete.memoryAllocation?.submission ?: -1
            println("  Run $i: heapAllocatedMemory=${results.complete.memoryBreakdown?.heapAllocatedMemory}, submission=$memory")
        }
    }

    "should load and print memory stats for Simple If Else" {
        val (question, report) = Validator.validate("Simple If Else")

        question.validated shouldBe true
        report shouldNotBe null

        val validationResults = question.validationResults
        validationResults shouldNotBe null

        println("=== Simple If Else Memory Statistics ===")
        println()

        println("Memory Allocation Limits:")
        println("  Java: ${validationResults!!.memoryAllocation.java}")
        println("  Kotlin: ${validationResults.memoryAllocation.kotlin}")
        println()

        println("Execution Counts:")
        println("  Java: ${validationResults.executionCounts.java}")
        println("  Kotlin: ${validationResults.executionCounts.kotlin}")
        println()

        validationResults.solutionMemoryBreakdown?.let { breakdown ->
            println("Solution Memory Breakdown:")
            breakdown.forEach { (language, memBreakdown) ->
                println("  $language:")
                println("    heapAllocatedMemory: ${memBreakdown.heapAllocatedMemory}")
                println("    maxCallStackSize: ${memBreakdown.maxCallStackSize}")
                println("    warmupMemory: ${memBreakdown.warmupMemory}")
                println("    warmupCount: ${memBreakdown.warmupCount}")
                println("    totalWithStack: ${memBreakdown.totalWithStack}")
                println("    totalWithWarmup: ${memBreakdown.totalWithWarmup}")
            }
            println()
        }

        validationResults.solutionAllocations?.let { allocations ->
            println("Solution Allocations (per test iteration):")
            allocations.forEach { (language, records) ->
                println("  $language (${records.size} records):")
                records.take(20).forEachIndexed { i, record ->
                    println("    [$i] bytes=${record.bytes}, caller=${record.callerClass}")
                }
                if (records.size > 20) {
                    println("    ... and ${records.size - 20} more")
                }
            }
            println()
        }

        println("Other Validation Info:")
        println("  Seed: ${validationResults.seed}")
        println("  Required Test Count: ${validationResults.requiredTestCount}")
        println("  Mutation Count: ${validationResults.mutationCount}")
        println("  Solution Max Runtime: ${validationResults.solutionMaxRuntime}")
        println("  Output Amount: ${validationResults.outputAmount}")
    }

    "should test correct solution and compare memory usage" {
        val (question, _) = Validator.validate("Simple If Else")
        question.validated shouldBe true

        // Get the correct solution
        val correctSolution = question.getCorrect(Language.java)!!
        println("=== Testing Correct Solution ===")
        println()
        println("Solution code:")
        println(correctSolution)
        println()

        // Run the test
        val results = question.test(correctSolution, Language.java)

        println("Test Results:")
        println("  Succeeded: ${results.succeeded}")
        println("  Timeout: ${results.timeout}")
        println("  Failed Steps: ${results.failedSteps}")
        println()

        results.complete.memoryAllocation?.let { memAlloc ->
            println("Memory Allocation Comparison:")
            println("  Solution (from validation): ${memAlloc.solution}")
            println("  Submission (this run):      ${memAlloc.submission}")
            println("  Limit:                      ${memAlloc.limit}")
            println("  Increase:                   ${memAlloc.increase}")
            println("  Failed:                     ${memAlloc.failed}")
            println()
        }

        results.complete.executionCount?.let { execCount ->
            println("Execution Count Comparison:")
            println("  Solution (from validation): ${execCount.solution}")
            println("  Submission (this run):      ${execCount.submission}")
            println("  Limit:                      ${execCount.limit}")
            println("  Increase:                   ${execCount.increase}")
            println("  Failed:                     ${execCount.failed}")
            println()
        }

        results.complete.memoryBreakdown?.let { breakdown ->
            println("Submission Memory Breakdown:")
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

        // Compare with validation results
        val validationResults = question.validationResults!!
        println("=== Comparison Summary ===")
        println("Validation memoryAllocation limit (Java): ${validationResults.memoryAllocation.java}")
        results.complete.memoryAllocation?.let {
            println("Test run submission memory:               ${it.submission}")
            println("Difference from validation solution:      ${it.increase}")
        }
    }
})
