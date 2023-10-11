@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.stumpers

import edu.illinois.cs.cs125.questioner.lib.Language
import java.math.BigInteger
import java.security.MessageDigest

fun String.md5() = BigInteger(1, MessageDigest.getInstance("MD5")!!.digest(toByteArray(Charsets.UTF_8)))
    .toString(16).padStart(32, '0')

fun String.toLanguage() = when (this) {
    "java" -> Language.java
    "kotlin" -> Language.kotlin
    else -> error("Invalid language $this")
}
