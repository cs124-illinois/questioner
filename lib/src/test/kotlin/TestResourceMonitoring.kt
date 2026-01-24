package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.ConfiguredSandboxPlugin
import edu.illinois.cs.cs125.jeed.core.Jacoco
import edu.illinois.cs.cs125.jeed.core.LineLimitExceeded
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jenisol.core.unwrap
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.beInstanceOf
import java.lang.reflect.Method
import kotlin.random.Random

@Suppress("UNUSED")
class TestResourceMonitoring : StringSpec({
    "should warm up successfully" {
        ResourceMonitoring.toString()
    }

    "should report checkpoints and total line counts".config(enabled = ResourceMonitoring.countLibraryLines) {
        val callLines = mutableListOf<Long>()
        val result = runJava("""
            public static void test(int times) {
                for (int i = 0; i < times; i++) {
                    System.out.println(i);
                }
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            println("Warmup")
            listOf(2, 1, 3).forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it)
                callLines.add(ResourceMonitoring.finishSubmissionCall().totalLines)
            }
        }
        result.threw should beNull()
        result.timeout shouldBe false
        result.completed shouldBe true
        callLines[0] shouldBeLessThan 600
        callLines[0] shouldBeGreaterThan 20
        callLines[1] shouldBeLessThan callLines[0]
        callLines[1] shouldBeGreaterThan 5
        callLines[2] shouldBeGreaterThan callLines[0]
        callLines[2] shouldBeGreaterThan callLines[1]
        val resourceResult = result.pluginResult(ResourceMonitoring)
        resourceResult.totalLines shouldBe callLines.sum()
    }

    "should limit total lines executed".config(enabled = ResourceMonitoring.countLibraryLines) {
        val result = runJava("""
            public static void test(byte[] values) {
                System.out.println("Start");
                java.util.Arrays.sort(values);
                System.out.println("Finish");
            }
        """.trimIndent(), ResourceMonitoringArguments(totalLineLimit = 1000)) { m ->
            val bytes = Random(1).nextBytes(500)
            unwrap { m(null, bytes) }
        }
        result.stdout shouldBe "Start"
        result.threw should beInstanceOf<LineLimitExceeded>()
    }

    "should report submission line counts" {
        val callLines = mutableListOf<Long>()
        val result = runJava("""
            public static void test(int times) {
                for (int i = 0; i < times; i++) {
                    System.out.println(i);
                }
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            listOf(2, 1, 3).forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it)
                callLines.add(ResourceMonitoring.finishSubmissionCall().submissionLines)
            }
        }
        result.threw should beNull()
        result.completed shouldBe true
        callLines[0] shouldBeLessThan 10
        callLines[0] shouldBeGreaterThan 4
        callLines[1] shouldBeLessThan callLines[0]
        callLines[1] shouldBeGreaterThan 2
        callLines[2] shouldBeGreaterThan callLines[0]
        callLines[2] shouldBeGreaterThan callLines[1]
        val resourceResult = result.pluginResult(ResourceMonitoring)
        resourceResult.submissionLines shouldBe callLines.sum()
    }

    "should limit submission lines executed" {
        val result = runJava("""
            public static int test(int times) {
                int sum = 0;
                for (int i = 0; i < times; i++) {
                    sum += i;
                }
                System.out.println(times);
                return sum;
            }
        """.trimIndent(), ResourceMonitoringArguments(submissionLineLimit = 1000)) { m ->
            unwrap {
                m(null, 10)
                m(null, 200)
                m(null, 1000)
            }
        }
        result.stdout shouldBe "10\n200"
        result.threw should beInstanceOf<LineLimitExceeded>()
    }

    "should track array allocation" {
        val callAllocs = mutableListOf<Long>()
        val result = runJava("""
            public static int[] test(int size) {
                return new int[size];
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            listOf(0, 20, 10, 50).forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it)
                callAllocs.add(ResourceMonitoring.finishSubmissionCall().allocatedMemory)
            }
        }
        result.threw should beNull()
        callAllocs[1] shouldBeGreaterThan 20
        callAllocs[1] shouldBeLessThan 1000 // Temporarily increased for allocation tracking overhead
        callAllocs[2] shouldBeGreaterThan 40
        callAllocs[2] shouldBeLessThan callAllocs[1]
        callAllocs[3] shouldBeGreaterThan 200
        val resourceResult = result.pluginResult(ResourceMonitoring)
        resourceResult.allocatedMemory shouldBeGreaterThan callAllocs.maxOf { it }
    }

    "should track string allocation" {
        val callAllocs = mutableListOf<Long>()
        val result = runJava("""
            public static String test(String input) {
                return input + input;
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            listOf("", "a", "this is a test", "test!").forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it)
                callAllocs.add(ResourceMonitoring.finishSubmissionCall().allocatedMemory)
            }
        }
        result.threw should beNull()
        callAllocs[1] shouldBeGreaterThan 20
        callAllocs[1] shouldBeLessThan 200
        callAllocs[2] shouldBeGreaterThan callAllocs[1]
        callAllocs[2] shouldBeLessThan 400
        callAllocs[3] shouldBeGreaterThan callAllocs[1]
        callAllocs[3] shouldBeLessThan callAllocs[2]
    }

    suspend fun testWarmupCharge(otherPlugins: List<ConfiguredSandboxPlugin<*, *>>, warmupMemoryLimit: Long) {
        val callLines = mutableListOf<Long>()
        val callAllocs = mutableListOf<Long>()
        val callWarmups = mutableListOf<Int>()
        val result = runJava("""
            public static String test(String input) {
                return input + ", and again: " + input;
            }
        """.trimIndent(), ResourceMonitoringArguments(), otherPlugins) { m ->
            listOf("a", "a", "this is a test", "test!").forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it)
                val checkpoint = ResourceMonitoring.finishSubmissionCall()
                callLines.add(checkpoint.totalLines)
                callAllocs.add(checkpoint.allocatedMemory)
                callWarmups.add(checkpoint.warmups)
            }
        }
        result.timeout shouldBe false
        result.completed shouldBe true
        result.threw should beNull()
        callWarmups[0] shouldBeGreaterThan 0
        callWarmups[1] shouldBe 0
        callWarmups[2] shouldBe 0
        callWarmups[3] shouldBe 0
        callLines[0] shouldBe callLines[1]
        callAllocs[0] shouldBeGreaterThan 20
        callAllocs[0] shouldBeLessThan warmupMemoryLimit // Can't bound tightly because of unpredictable JIT allocations
        callAllocs[2] shouldBeGreaterThan callAllocs[1]
        callAllocs[2] shouldBeLessThan 1000
        callAllocs[3] shouldBeGreaterThan callAllocs[1]
        callAllocs[3] shouldBeLessThan callAllocs[2]
    }

    "should reduce the one-time warmup charge to checkpoints" {
        testWarmupCharge(listOf(), 1000)
    }

    "should reduce the warmup charge from Jacoco" {
        testWarmupCharge(listOf(ConfiguredSandboxPlugin(Jacoco, Unit)), 2000)
    }

    "should limit allocated memory" {
        val result = runJava("""
            public static void test() {
                System.out.println("Start");
                for (int i = 0; ; i++) {
                    int[] ints = new int[i];
                    System.out.println(i);
                }
            }
        """.trimIndent(), ResourceMonitoringArguments(allocatedMemoryLimit = 500000)) { m ->
            unwrap { m(null) }
        }
        result.timeout shouldBe false
        result.stdout shouldStartWith "Start"
        result.stdout shouldContain "\n20\n"
        result.threw should beInstanceOf<OutOfMemoryError>()
    }

    "should prevent huge direct allocations" {
        val result = runJava("""
            public static void test() {
                System.out.println("Start");
                int[] huge = new int[500000];
            }
        """.trimIndent(), ResourceMonitoringArguments(allocatedMemoryLimit = 500000)) { m ->
            unwrap { m(null) }
        }
        result.stdout shouldStartWith "Start"
        result.threw should beInstanceOf<OutOfMemoryError>()
        result.pluginResult(ResourceMonitoring).allocatedMemory shouldBeLessThan 100000
    }

    "should prevent huge indirect allocations" {
        val result = runJava("""
            public static void test() {
                System.out.println("Start");
                new java.util.ArrayList<Object>(200000);
            }
        """.trimIndent(), ResourceMonitoringArguments(allocatedMemoryLimit = 500000)) { m ->
            unwrap { m(null) }
        }
        result.stdout shouldStartWith "Start"
        result.threw should beInstanceOf<OutOfMemoryError>()
        result.pluginResult(ResourceMonitoring).allocatedMemory shouldBeLessThan 100000
    }

    "should exclude memory allocated post-submission if asked" {
        var allocatedMemory = 0L
        val result = runJava("""
            public static int test() {
                return 255;
            }
        """.trimIndent(), ResourceMonitoringArguments(allocatedMemoryLimit = 500000)) { m ->
            ResourceMonitoring.beginSubmissionCall(true)
            m(null)
            IntArray(2000)
            allocatedMemory = ResourceMonitoring.finishSubmissionCall().allocatedMemory
            IntArray(3000)
        }
        allocatedMemory shouldBeLessThan 1000
        result.pluginResult(ResourceMonitoring).allocatedMemory shouldBeLessThan 1000
    }

    "should report allocations semi-consistently" {
        val random = Random(124)
        val allocations = mutableListOf<Long>()
        val result = runJava("""
            public static int test(int number) {
                return number + 1;
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            repeat(5000) {
                val number = random.nextInt()
                ResourceMonitoring.beginSubmissionCall()
                m(null, number)
                val checkpoint = ResourceMonitoring.finishSubmissionCall()
                allocations.add(checkpoint.allocatedMemory)
            }
        }
        result.timeout shouldBe false
        result.completed shouldBe true
        allocations.minOrNull()!! shouldBeGreaterThan 16
        allocations.maxOrNull()!! shouldBeLessThan 1500 // Not exact due to JIT
        allocations.average() shouldBeLessThan 200.0
        result.pluginResult(ResourceMonitoring).allocatedMemory shouldBeLessThan 3000
    }

    "should estimate memory used by max stack depth" {
        // allocatedMemory includes both call stack size and regular allocations
        // The latter is "an approximation" due to possible recording delay, so shouldn't compare it exactly
        // In practice it seems the JIT compiler nondeterministically performs native allocations we can't measure
        // maxCallStackSize is not subject to fluctuations, so it can be compared for equality
        val iterativeStacks = mutableListOf<Long>()
        runJava("""
            public static long test(long i) {
                long result = 1;
                while (i > 0) {
                    result *= i;
                    i--;
                }
                return result;
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            listOf(0, 1, 5, 15).forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it.toLong())
                iterativeStacks.add(ResourceMonitoring.finishSubmissionCall().maxCallStackSize)
            }
        }
        iterativeStacks[1] shouldBeLessThan 300
        iterativeStacks[1] shouldBeGreaterThan 20
        iterativeStacks[2] shouldBe iterativeStacks[1]
        iterativeStacks[3] shouldBe iterativeStacks[1]
        // Changes in stack depth should be visible in the noise of allocation recording fluctuation
        val recursiveAllocs = mutableListOf<Long>()
        runJava("""
            public static long test(long i) {
                if (i <= 1) {
                    return i;
                } else {
                    return i * test(i - 1);
                }
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            listOf(0, 1, 5, 16).forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it.toLong())
                recursiveAllocs.add(ResourceMonitoring.finishSubmissionCall().allocatedMemory)
            }
        }
        recursiveAllocs[1] shouldBeLessThan 300
        recursiveAllocs[1] shouldBeGreaterThan 20
        recursiveAllocs[2] shouldBeGreaterThan 4 * recursiveAllocs[1]
        recursiveAllocs[3] shouldBeGreaterThan 3 * recursiveAllocs[2]
        // Should all be exactly the same stack, compare maxCallStackSize for equality
        val nonrecursiveCallStacks = mutableListOf<Long>()
        runJava("""
            public static long test(long i) {
                long result = 1;
                while (i > 0) {
                    result = multiply(result, i);
                    i--;
                }
                return result;
            }
            private static long multiply(long a, long b) {
                return a * b;
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            listOf(0, 1, 5, 15).forEach {
                ResourceMonitoring.beginSubmissionCall()
                m(null, it.toLong())
                nonrecursiveCallStacks.add(ResourceMonitoring.finishSubmissionCall().maxCallStackSize)
            }
        }
        nonrecursiveCallStacks[1] shouldBeLessThan 400
        nonrecursiveCallStacks[1] shouldBeGreaterThan 20
        nonrecursiveCallStacks[2] shouldBe nonrecursiveCallStacks[1]
        nonrecursiveCallStacks[3] shouldBe nonrecursiveCallStacks[1]
    }

    "should identify recursive methods" {
        val recursiveMethods = mutableSetOf<ResourceMonitoringResults.MethodInfo>()
        val result = runJava("""
            public static long test(long i) { return factorial(i); }
            public static long factorial(long i) {
                if (i <= 1) {
                    return i;
                } else {
                    return i * factorial(i - 1);
                }
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            ResourceMonitoring.beginSubmissionCall()
            m(null, 2L)
            recursiveMethods.addAll(ResourceMonitoring.finishSubmissionCall().invokedRecursiveFunctions)
        }
        recursiveMethods shouldHaveSize 1
        recursiveMethods.first().methodName shouldBe "factorial"
        recursiveMethods.first().descriptor shouldBe "(J)J"
        result.pluginResult(ResourceMonitoring).invokedRecursiveFunctions shouldHaveSize 1
    }

    "should not consider property equals recursive" {
        val recursiveMethods = mutableSetOf<ResourceMonitoringResults.MethodInfo>()
        runJava("""
            public static void test() {
                Item a = new Item();
                a.name = "A";
                Item b = new Item();
                b.name = "B";
                a.equals(b);
            }
            private static class Item {
                private String name;
                public boolean equals(Object other) {
                    if (other instanceof Item i) {
                        return name.equals(i.name);
                    } else {
                        return false;
                    }
                }
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            ResourceMonitoring.beginSubmissionCall()
            m(null)
            recursiveMethods.addAll(ResourceMonitoring.finishSubmissionCall().invokedRecursiveFunctions)
        }
        recursiveMethods shouldHaveSize 0
    }

    "should give true negatives on recursion with exceptions" {
        val recursiveMethods = mutableSetOf<ResourceMonitoringResults.MethodInfo>()
        val result = runJava("""
            public static void test() {
                try {
                    System.out.println(divide(10, 0));
                } catch (ArithmeticException e) {
                    System.out.println(divide(10, 5));
                }
            }
            private static int divide(int a, int b) {
                return a / b;
            }
        """.trimIndent(), ResourceMonitoringArguments()) { m ->
            ResourceMonitoring.beginSubmissionCall()
            m(null)
            recursiveMethods.addAll(ResourceMonitoring.finishSubmissionCall().invokedRecursiveFunctions)
        }
        result.stdout shouldBe "2"
        recursiveMethods shouldHaveSize 0
    }
})

private suspend fun runJava(
    code: String,
    args: ResourceMonitoringArguments,
    otherPlugins: List<ConfiguredSandboxPlugin<*, *>> = listOf(),
    test: (Method) -> Unit
): Sandbox.TaskResults<*> {
    val compiledSource = Source.fromJava("public class Test {\n$code\n}").compile()
    val plugins = otherPlugins + listOf(ConfiguredSandboxPlugin(ResourceMonitoring, args))
    return Sandbox.execute(
        compiledSource.classLoader,
        configuredPlugins = plugins,
        // executionArguments = Sandbox.ExecutionArguments(timeout = 120000) // For testing
    ) { (cl, _) ->
        val method = cl.loadClass("Test").methods.find { it.name == "test" }!!
        test(method)
    }
}
