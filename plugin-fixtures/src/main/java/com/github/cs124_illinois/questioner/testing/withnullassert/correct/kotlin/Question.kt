package com.github.cs124_illinois.questioner.testing.withnullassert.correct.kotlin

import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect

/*
 * For test correctness testing to ensure AssertionErrors pop through.
 */

@AlsoCorrect
class Question {
  fun stringLength(input: String?): Int {
    assert(input != null)
    return input!!.length
  }
}