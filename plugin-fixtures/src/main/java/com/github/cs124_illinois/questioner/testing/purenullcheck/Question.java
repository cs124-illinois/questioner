package com.github.cs124_illinois.questioner.testing.purenullcheck;

import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.questioner.lib.Wrap;

/*
 * Test Kotlin alignment with Java null checks.
 */

@Correct(name = "Pure null check", author = "challen@illinois.edu", version = "2024.10.0")
@Wrap
public class Question {
  boolean nullCheck(String value, boolean toReturn) {
    assert value != null;
    return toReturn;
  }
}
