package com.github.cs124_illinois.questioner.testing.external.addtwo;

import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.questioner.lib.Wrap;

/*
 * Write a method `addTwo` that returns its `int` argument plus two.
 */

@Correct(name = "Add Two", author = "external@illinois.edu", version = "2024.1.0")
@Wrap(autoStarter = true)
public class Question {
  int addTwo(int value) {
    return value + 2;
  }
}
