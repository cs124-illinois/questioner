package com.github.cs124_illinois.questioner.testing.addone.incorrect.extraoutput;

import edu.illinois.cs.cs125.questioner.lib.Incorrect;

@Incorrect("extraoutput")
public class Question {
  int addOne(int value) {
    System.out.println(value);
    return value + 1;
  }
}
