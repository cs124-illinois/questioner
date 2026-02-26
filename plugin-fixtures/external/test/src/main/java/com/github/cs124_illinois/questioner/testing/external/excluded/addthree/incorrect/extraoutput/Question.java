package com.github.cs124_illinois.questioner.testing.external.excluded.addthree.incorrect.extraoutput;

import edu.illinois.cs.cs125.questioner.lib.Incorrect;

@Incorrect("extraoutput")
public class Question {
  int addThree(int value) {
    System.out.println(value);
    return value + 3;
  }
}
