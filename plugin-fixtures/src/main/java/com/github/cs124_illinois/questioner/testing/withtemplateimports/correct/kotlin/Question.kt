@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.github.cs124_illinois.questioner.testing.withtemplateimports.correct.kotlin

import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect
import java.util.Arrays
import java.util.List

/*
 * Example to test use of import statements in method templates.
 */

@AlsoCorrect
class Question {
  fun max(first: Int, second: Int): List<Int> = Arrays.asList(first, second) as List<Int>
}