package io.kotest.provided

import edu.illinois.cs.cs125.jeed.core.configureJeedLogging
import io.kotest.core.config.AbstractProjectConfig
import java.util.logging.Level

/**
 * Jeed no longer ships a logging backend, so there is no logback.xml to read. Configure
 * java.util.logging directly instead, mirroring the levels the old logback.xml set.
 *
 * Do not replace `System.out` or `System.err` here. Jeed installs its redirecting streams when the
 * sandbox starts and checks afterwards whether anything swapped them out; a replacement silently
 * takes over the routing that keeps sandboxed output out of the console.
 */
object ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        configureJeedLogging(jeedLevel = Level.WARNING, rootLevel = Level.WARNING)
    }
}
