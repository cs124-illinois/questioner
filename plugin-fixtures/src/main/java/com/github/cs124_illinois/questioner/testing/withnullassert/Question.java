package com.github.cs124_illinois.questioner.testing.withnullassert;

import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.questioner.lib.Wrap;

/*
 * For test correctness testing to ensure AssertionErrors pop through.
 */

@Correct(name = "With null assert", author = "challen@illinois.edu", version = "2024.9.0")
@Wrap
public class Question {
  int stringLength(String input) {
    assert input != null;
    return input.length();
  }
}
