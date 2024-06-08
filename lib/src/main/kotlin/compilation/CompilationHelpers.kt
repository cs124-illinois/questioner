package edu.illinois.cs.cs125.questioner.lib.compilation

import edu.illinois.cs.cs125.jeed.core.CompilationMessage

fun List<CompilationMessage>.removeExpected() = filter {
    !(it.message.contains("auxiliary class") && it.message.contains("should not be accessed from outside its own source file")) &&
        !(it.message.contains("overrides equals, but neither it nor any superclass overrides hashCode method"))
}
fun List<CompilationMessage>.filterSuppressions(suppressions: Set<String>) = let { messages ->
    when {
        suppressions.contains("rawtypes") -> messages.filter { !(it.message.contains("raw type")) }
        else -> messages
    }
}.let { messages ->
    when {
        suppressions.contains("unchecked") -> messages.filter { !(it.message.contains("unchecked")) }
        else -> messages
    }
}.let { messages ->
    when {
        suppressions.contains("divzero") -> messages.filter { !(it.message.contains("division by zero")) }
        else -> messages
    }
}.let { messages ->
    when {
        suppressions.contains("this-escape") -> messages.filter { !(it.message.contains("'this' escape")) }
        else -> messages
    }
}