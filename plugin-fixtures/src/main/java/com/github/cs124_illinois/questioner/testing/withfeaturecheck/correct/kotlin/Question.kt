package com.github.cs124_illinois.questioner.testing.withfeaturecheck.correct.kotlin

import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect

/*
 * For testing features.
 */

@AlsoCorrect
class Question {
  fun sumTo(range: Int): Int = range * (range + 1) / 2
}