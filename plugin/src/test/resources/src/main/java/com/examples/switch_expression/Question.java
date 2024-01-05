package edu.northeastern.examples.switch_expression;

import edu.illinois.cs.cs125.jeed.core.FeatureName;
import edu.illinois.cs.cs125.jeed.core.Features;
import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import edu.illinois.cs.cs125.questioner.lib.CheckFeatures;
import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.questioner.lib.FeatureCheckException;
import edu.illinois.cs.cs125.questioner.lib.Wrap;
import edu.illinois.cs.cs125.questioner.lib.features.FeatureHelpers;
import java.util.ArrayList;
import java.util.List;

/*
 * Write a function `determineStatus()` that takes an `int` argument. If the value is 0 or 1,
 * it should return `"neither prime nor composite"`. If the value is 2, 3, 5, or 7, it should
 * return `"prime"`. If the value is 4, 6, 8, or 9, it should return "composite". If it is any
 * other value, it should return `"out of range"`. Use a `switch` expression.
 */
@Wrap
@Correct(
    name = "Switch Expression for Primes",
    author = "e.spertus@northeastern.edu",
    version = "2024.1.0")
public class Question {
  String determineStatus(int n) {
    return switch (n) {
      case 0, 1 -> "neither prime nor composite";
      case 2, 3, 5, 7 -> "prime";
      case 4, 6, 8, 9 -> "composite";
      default -> "out of range";
    };
  }

  @FixedParameters
  private static final List<Integer> FIXED = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -1);

  @CheckFeatures
  private static List<String> checkFeatures(Features solution, Features submission) {
    if (FeatureHelpers.hasFeatures(
        submission,
        List.of(FeatureName.IF_STATEMENTS, FeatureName.IF_EXPRESSIONS, FeatureName.SWITCH))) {
      throw new FeatureCheckException("Submission uses an if statement");
    }
    return new ArrayList<>();
  }
}
