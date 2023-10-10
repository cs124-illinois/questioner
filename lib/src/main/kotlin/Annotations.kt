@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.Features
import java.lang.RuntimeException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

@Suppress("LongParameterList")
@Target(AnnotationTarget.CLASS)
annotation class Correct(
    val name: String,
    val version: String,
    val author: String = "",
    val path: String = "",
    val solutionThrows: Boolean = Question.TestingControl.DEFAULT_SOLUTION_THROWS,
    val focused: Boolean = Question.Metadata.DEFAULT_FOCUSED,
    val publish: Boolean = Question.Metadata.DEFAULT_PUBLISH,
    val minTestCount: Int = Question.TestingControl.DEFAULT_MIN_TEST_COUNT,
    val maxTestCount: Int = Question.TestingControl.DEFAULT_MAX_TEST_COUNT,
    val minTimeout: Int = Question.TestingControl.DEFAULT_MIN_TIMEOUT,
    val maxTimeout: Int = Question.TestingControl.DEFAULT_MAX_TIMEOUT,
    val timeoutMultiplier: Int = Question.TestingControl.DEFAULT_TIMEOUT_MULTIPLIER,
    val minMutationCount: Int = Question.TestingControl.DEFAULT_MIN_MUTATION_COUNT,
    val maxMutationCount: Int = -1,
    val outputMultiplier: Int = Question.TestingControl.DEFAULT_OUTPUT_MULTIPLIER,
    val maxExtraComplexity: Int = Question.TestingControl.DEFAULT_MAX_EXTRA_COMPLEXITY,
    val maxDeadCode: Int = Question.TestingControl.DEFAULT_MAX_DEAD_CODE,
    val maxExecutionCountMultiplier: Long = Question.TestingControl.DEFAULT_MAX_EXECUTION_COUNT_MULTIPLIER,
    val executionCountFailureMultiplier: Int = Question.TestingControl.DEFAULT_EXECUTION_COUNT_FAILURE_MULTIPLIER,
    val executionCountTimeoutMultiplier: Int = Question.TestingControl.DEFAULT_EXECUTION_COUNT_TIMEOUT_MULTIPLIER,
    val allocationFailureMultiplier: Double = Question.TestingControl.DEFAULT_ALLOCATION_FAILURE_MULTIPLIER,
    val allocationLimitMultiplier: Double = Question.TestingControl.DEFAULT_ALLOCATION_LIMIT_MULTIPLIER,
    val minExtraSourceLines: Int = Question.TestingControl.DEFAULT_MIN_EXTRA_SOURCE_LINES,
    val sourceLinesMultiplier: Double = Question.TestingControl.DEFAULT_SOURCE_LINES_MULTIPLIER,
    val seed: Int = Question.TestingControl.DEFAULT_SEED,
    val maxComplexityMultiplier: Int = Question.TestingControl.DEFAULT_MAX_COMPLEXITY_MULTIPLIER,
    val maxLineCountMultiplier: Int = Question.TestingControl.DEFAULT_MAX_LINECOUNT_MULTIPLIER,
    val maxClassSizeMultiplier: Int = Question.TestingControl.DEFAULT_MAX_CLASSSIZE_MULTIPLIER,
)

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class Incorrect(val reason: String = "test")

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class Starter

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class AlsoCorrect

@Target(AnnotationTarget.CLASS)
annotation class Whitelist(val paths: String)

@Target(AnnotationTarget.CLASS)
annotation class Blacklist(val paths: String)

@Target(AnnotationTarget.CLASS)
annotation class CheckstyleSuppress(val suppressions: String)

@Target(AnnotationTarget.CLASS)
annotation class TemplateImports(val paths: String)

@Target(AnnotationTarget.CLASS)
annotation class Wrap(val autoStarter: Boolean = false)

@Target(AnnotationTarget.CLASS)
annotation class Cite(val source: String, val link: String = "")

@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class Ignore

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckFeatures {
    companion object {
        val name: String = CheckFeatures::class.java.simpleName
        fun validate(method: Method) {
            check(Modifier.isStatic(method.modifiers)) { "@$name methods must be static" }
            check(method.parameterTypes.size == 2 && method.parameterTypes[0] == Features::class.java && method.parameterTypes[0] == Features::class.java) {
                "@$name methods must accept parameters (Features solution, Features submission)"
            }
            check(method.genericReturnType is ParameterizedType) {
                "@$name methods must return List<String>!"
            }
            (method.genericReturnType as ParameterizedType).also { collectionType ->
                check(collectionType.rawType == java.util.List::class.java) {
                    "@$name methods must return List<String>"
                }
                check(collectionType.actualTypeArguments.size == 1) {
                    "@$name methods must return List<String>"
                    check(collectionType.actualTypeArguments.first()::class.java == String::class.java) {
                        "@$name methods must return List<String>"
                    }
                }
            }
            method.isAccessible = true
        }
    }
}

fun Method.isCheckFeatures() = isAnnotationPresent(CheckFeatures::class.java)

class FeatureCheckException(message: String) : RuntimeException(message)

