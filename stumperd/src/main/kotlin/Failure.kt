package edu.illinois.cs.cs124.stumperd.server

import java.time.Instant

sealed class StumperFailure(val step: Steps, val timestamp: Instant, val id: String, cause: Throwable) : Exception(cause) {
    constructor(step: Steps, submission: Submission, cause: Throwable): this(step, submission.timestamp, submission.id, cause)
}