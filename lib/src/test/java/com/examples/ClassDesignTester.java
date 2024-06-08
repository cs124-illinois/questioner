package com.examples;

import java.util.function.Function;

public class ClassDesignTester {
    public static Function<Integer, Integer> generateAddOne() {
        return (v) -> v + 1;
    }
}
