package io.kotest.provided

import edu.illinois.cs.cs125.jeed.core.configureJeedLogging
import io.kotest.core.config.AbstractProjectConfig
import java.util.logging.Level

/**
 * Replaces the logback-test.xml this module used to carry, now that Jeed routes SLF4J into
 * java.util.logging rather than shipping logback.
 */
object ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        configureJeedLogging(jeedLevel = Level.FINE, rootLevel = Level.INFO)
    }
}
