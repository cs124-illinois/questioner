@file:Suppress("ktlint:standard:filename")

package edu.illinois.cs.cs124.stumperd.server

sealed class StumperFailure(val step: Steps, cause: Throwable) : Exception(cause)
