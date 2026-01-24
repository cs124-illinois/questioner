package com.github.cs124_illinois.questioner.testing.purenullcheck.correct.kotlin

import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect

/*
 * Test Kotlin alignment with Java null checks.
 */

@AlsoCorrect
class Question {
  @Suppress("UNUSED_PARAMETER")
  fun nullCheck(value: String, toReturn: Boolean): Boolean = toReturn
}