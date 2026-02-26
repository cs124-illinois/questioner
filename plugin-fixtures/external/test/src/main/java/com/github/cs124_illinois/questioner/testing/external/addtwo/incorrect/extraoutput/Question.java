package com.github.cs124_illinois.questioner.testing.external.addtwo.incorrect.extraoutput;

import edu.illinois.cs.cs125.questioner.lib.Incorrect;

@Incorrect("extraoutput")
public class Question {
  int addTwo(int value) {
    System.out.println(value);
    return value + 2;
  }
}
