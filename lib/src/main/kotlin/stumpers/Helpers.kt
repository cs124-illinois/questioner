@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.stumpers

import edu.illinois.cs.cs125.questioner.lib.Question
import java.math.BigInteger
import java.security.MessageDigest

fun String.md5() = BigInteger(1, MessageDigest.getInstance("MD5")!!.digest(toByteArray(Charsets.UTF_8)))
    .toString(16).padStart(32, '0')

private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$".toRegex()

fun String.isEmail() = matches(EMAIL_REGEX)

fun String.toLanguage() = when (this) {
    "java" -> Question.Language.java
    "kotlin" -> Question.Language.kotlin
    else -> error("Invalid language $this")
}
