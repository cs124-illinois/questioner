package edu.illinois.cs.cs125.questioner.lib.verifiers

import java.util.Base64

fun String.toBase64(): String = Base64.getEncoder().encodeToString(toByteArray(Charsets.UTF_8))

fun String.fromBase64() = String(Base64.getDecoder().decode(this))
