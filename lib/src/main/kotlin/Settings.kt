package edu.illinois.cs.cs125.questioner.lib

import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.sync.Semaphore

internal const val MAX_INDIVIDUAL_ALLOCATION_BYTES: Long = 1024 * 1024
internal const val MIN_ALLOCATION_FAILURE_BYTES: Long = 2 * 1024 // Account for nondeterminism due to JIT
internal const val MIN_ALLOCATION_LIMIT_BYTES: Long = 2 * 1024 * 1024 // Leave room for concat in println debugging

internal val dotenv: Dotenv = Dotenv.configure().ignoreIfMissing().load()
internal val questionerMaxConcurrency = dotenv.get("QUESTIONER_MAX_CONCURRENCY")?.toInt() ?: Int.MAX_VALUE
internal val testingLimiter = Semaphore(questionerMaxConcurrency)

internal const val QUESTIONER_DEFAULT_TEST_TIMEOUT_MS = 80L
internal val questionerTestTimeoutMS =
    dotenv.get("QUESTIONER_TEST_TIMEOUT_MS")?.toLong() ?: QUESTIONER_DEFAULT_TEST_TIMEOUT_MS
internal const val QUESTIONER_DEFAULT_WALL_CLOCK_TIMEOUT_MULTIPLIER = 32
internal val questionerWallClockTimeoutMultiplier =
    dotenv.get("QUESTIONER_WALL_CLOCK_TIMEOUT_MULTIPLIER")?.toInt() ?: QUESTIONER_DEFAULT_WALL_CLOCK_TIMEOUT_MULTIPLIER
internal const val QUESTIONER_DEFAULT_TESTTEST_TIMEOUT_MS = 160L
internal val questionerTestTestTimeoutMS =
    dotenv.get("QUESTIONER_TESTTEST_TIMEOUT_MS")?.toLong() ?: QUESTIONER_DEFAULT_TESTTEST_TIMEOUT_MS