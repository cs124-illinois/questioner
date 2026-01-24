package com.github.cs124_illinois.questioner.testing.badextraprivatevariable;

public class Parent {
  private final String lastName;

  public Parent(String setLastName) {
    lastName = setLastName;
  }

  public String getLastName() {
    return lastName;
  }
}
