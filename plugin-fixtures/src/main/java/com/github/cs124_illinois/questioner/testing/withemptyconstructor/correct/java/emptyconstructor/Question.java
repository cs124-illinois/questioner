package com.github.cs124_illinois.questioner.testing.withemptyconstructor.correct.java.emptyconstructor;

import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect;

@AlsoCorrect
public class Question {
  private int value;

  public Question() {
    value = 0;
  }

  int next() {
    return value++;
  }
}
