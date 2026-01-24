package com.github.cs124_illinois.questioner.testing.simpleifelse;

import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.questioner.lib.Wrap;
import java.util.Arrays;
import java.util.List;

/*
 * Write a method `checkValue` that prints "positive" if its `int` argument is greater than zero,
 * or "non-positive" otherwise.
 */

@Correct(name = "Simple If Else", author = "challen@illinois.edu", version = "2026.1.0")
@Wrap
public class Question {
  void checkValue(int value) {
    if (value > 0) {
      System.out.println("positive");
    } else {
      System.out.println("non-positive");
    }
  }

  @FixedParameters
  private static final List<Integer> FIXED = Arrays.asList(-10, -1, 0, 1, 10, 100);
}
