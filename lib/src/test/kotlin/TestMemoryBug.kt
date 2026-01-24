package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.questioner.lib.serialization.json
import io.kotest.core.spec.style.StringSpec

class TestMemoryBug : StringSpec({

    "Compare calibration vs testing settings" {
        val questions = json.decodeFromString<List<Question>>(
            object {}::class.java.getResource("/questions.json")!!.readText()
        ).associateBy { it.published.path }

        val question = questions["simple-if-else"]!!

        // Run fresh validation to capture calibration settings
        question.warm()
        question.validate(124, 64)

        val calibrationSettings = question.validationResults?.calibrationSettings
        val testingSettings = question.testingSettings!!

        println("=== Calibration Settings (used during validation) ===")
        if (calibrationSettings != null) {
            println("  seed: ${calibrationSettings.seed}")
            println("  testCount: ${calibrationSettings.testCount}")
            println("  outputLimit: ${calibrationSettings.outputLimit}")
            println("  perTestOutputLimit: ${calibrationSettings.perTestOutputLimit}")
            println("  executionCountLimit: ${calibrationSettings.executionCountLimit}")
            println("  allocationLimit: ${calibrationSettings.allocationLimit}")
        } else {
            println("  (calibrationSettings is null)")
        }
        println()

        println("=== Testing Settings (used for submission testing) ===")
        println("  seed: ${testingSettings.seed}")
        println("  testCount: ${testingSettings.testCount}")
        println("  outputLimit: ${testingSettings.outputLimit}")
        println("  perTestOutputLimit: ${testingSettings.perTestOutputLimit}")
        println("  executionCountLimit: ${testingSettings.executionCountLimit}")
        println("  allocationLimit: ${testingSettings.allocationLimit}")
        println()

        if (calibrationSettings != null) {
            println("=== Key Differences ===")
            println("  outputLimit: ${calibrationSettings.outputLimit} (calibration) vs ${testingSettings.outputLimit} (testing)")
            println("  perTestOutputLimit: ${calibrationSettings.perTestOutputLimit} (calibration) vs ${testingSettings.perTestOutputLimit} (testing)")
            println("  executionCountLimit: ${calibrationSettings.executionCountLimit} (calibration) vs ${testingSettings.executionCountLimit} (testing)")
            println("  allocationLimit: ${calibrationSettings.allocationLimit} (calibration) vs ${testingSettings.allocationLimit} (testing)")
        }
    }

    "Test simple method without System.out" {
        // Create a minimal test - just call a method that does arithmetic
        val questions = json.decodeFromString<List<Question>>(
            object {}::class.java.getResource("/questions.json")!!.readText()
        ).associateBy { it.published.path }

        // Compare questions with different characteristics
        println("=== Comparing different question types ===")
        println()

        val testCases = listOf(
            "add-one" to "no I/O",
            "simple-if-else" to "uses System.out.println"
        )

        for ((path, description) in testCases) {
            val question = questions[path] ?: continue
            val solution = question.getCorrect(Language.java) ?: continue
            val validationBreakdown = question.validationResults?.solutionMemoryBreakdown?.get(Language.java)

            println("=== $path ($description) ===")
            println("Validation: heap=${validationBreakdown?.heapAllocatedMemory}, warmup=${validationBreakdown?.warmupMemory}, count=${validationBreakdown?.warmupCount}")

            // Run 8 times to see stabilization
            for (i in 1..8) {
                val results = question.test(solution, Language.java)
                val breakdown = results.complete.memoryBreakdown!!
                val heapDiff = breakdown.heapAllocatedMemory - (validationBreakdown?.heapAllocatedMemory ?: 0)
                val warmupDiff = breakdown.warmupMemory - (validationBreakdown?.warmupMemory ?: 0)
                println("Run $i: heap=${breakdown.heapAllocatedMemory} (${if (heapDiff >= 0) "+" else ""}$heapDiff), " +
                    "warmup=${breakdown.warmupMemory} (${if (warmupDiff >= 0) "+" else ""}$warmupDiff), " +
                    "count=${breakdown.warmupCount}")
            }
            println()
        }
    }

    "Test simple-if-else with fresh validation" {
        // Load questions and run fresh validation to compare calibration results
        val questions = json.decodeFromString<List<Question>>(
            object {}::class.java.getResource("/questions.json")!!.readText()
        ).associateBy { it.published.path }

        val question = questions["simple-if-else"]!!
        val solution = question.getCorrect(Language.java)!!
        val storedBreakdown = question.validationResults!!.solutionMemoryBreakdown?.get(Language.java)

        println("=== Solution Code ===")
        println(solution)
        println()

        println("=== Stored Validation Results ===")
        println("  heapAllocatedMemory: ${storedBreakdown?.heapAllocatedMemory}")
        println("  warmupMemory:        ${storedBreakdown?.warmupMemory}")
        println("  warmupCount:         ${storedBreakdown?.warmupCount}")
        println()

        // Run test() 8 times BEFORE validation
        println("=== Test runs BEFORE validation (using stored results) ===")
        for (i in 1..8) {
            val results = question.test(solution, Language.java)
            val breakdown = results.complete.memoryBreakdown!!
            val heapDiff = breakdown.heapAllocatedMemory - (storedBreakdown?.heapAllocatedMemory ?: 0)
            println("Run $i: heap=${breakdown.heapAllocatedMemory} (${if (heapDiff >= 0) "+" else ""}$heapDiff from stored)")
        }
        println()

        // Run simple-if-else until drop
        println("=== Run simple-if-else until drop ===")
        for (i in 1..20) {
            val results = question.test(solution, Language.java)
            val heap = results.complete.memoryBreakdown!!.heapAllocatedMemory
            println("Run $i: heap=$heap")
            if (heap < 1000) {
                println("DROPPED!")
                break
            }
        }

        // Confirm low
        println("\n=== Confirm LOW ===")
        for (i in 1..3) {
            val results = question.test(solution, Language.java)
            println("Run $i: heap=${results.complete.memoryBreakdown!!.heapAllocatedMemory}")
        }

        // Try solution with unique bytecode by adding unique local variable to method body
        println("\n=== Try solution with unique bytecode ===")
        for (i in 1..5) {
            // Add a unique local variable assignment at the start of the method body
            val uniqueSolution = solution.replaceFirst(
                "void checkValue(int value) {",
                "void checkValue(int value) { int __unique_${System.nanoTime()} = $i;"
            )
            println("Unique solution $i:\n$uniqueSolution")
            val results = question.test(uniqueSolution, Language.java)
            val breakdown = results.complete.memoryBreakdown
            if (breakdown != null) {
                println("Unique bytecode $i: heap=${breakdown.heapAllocatedMemory}")
            } else {
                println("Unique bytecode $i: memoryBreakdown is null, failedSteps=${results.failedSteps}")
            }
        }
    }

    "Test add-one (no System.out) for comparison" {
        val questions = json.decodeFromString<List<Question>>(
            object {}::class.java.getResource("/questions.json")!!.readText()
        ).associateBy { it.published.path }

        val question = questions["add-one"]!!
        val solution = question.getCorrect(Language.java)!!
        val validationBreakdown = question.validationResults!!.solutionMemoryBreakdown?.get(Language.java)

        println("=== add-one Validation Memory Breakdown ===")
        if (validationBreakdown != null) {
            println("  heapAllocatedMemory: ${validationBreakdown.heapAllocatedMemory}")
            println("  warmupMemory:        ${validationBreakdown.warmupMemory}")
            println("  warmupCount:         ${validationBreakdown.warmupCount}")
        } else {
            println("  (no breakdown stored)")
        }
        println()

        println("=== add-one Test Run Memory Breakdowns ===")
        for (i in 1..3) {
            ResourceMonitoring.markSandboxThread()
            val results = question.test(solution, Language.java)
            val breakdown = results.complete.memoryBreakdown!!
            println("Run $i:")
            println("  heapAllocatedMemory: ${breakdown.heapAllocatedMemory}")
            println("  warmupMemory:        ${breakdown.warmupMemory}")
            println("  warmupCount:         ${breakdown.warmupCount}")
            println("  warmupThreadMismatches: ${ResourceMonitoring.getWarmupThreadMismatchCount()}")
            if (validationBreakdown != null) {
                val heapDiff = breakdown.heapAllocatedMemory - validationBreakdown.heapAllocatedMemory
                println("  DIFF from validation: heap=${heapDiff}")
            }
            println()
        }
    }
})
