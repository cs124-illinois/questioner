package com.github.cs124_illinois.questioner.testing.external.excluded.addthree;

import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.questioner.lib.Wrap;

/*
 * Write a method `addThree` that returns its `int` argument plus three.
 */

@Correct(name = "Add Three", author = "external@illinois.edu", version = "2024.1.0")
@Wrap(autoStarter = true)
public class Question {
  int addThree(int value) {
    return value + 3;
  }
}
