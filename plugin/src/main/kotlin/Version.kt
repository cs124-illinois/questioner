package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.VERSION
import io.github.z4kn4fein.semver.toVersion
import org.gradle.testfixtures.ProjectBuilder

fun getLatestQuestionerVersion(): String? {
    val project = ProjectBuilder.builder().build()
    val queryConfig = project.configurations.create("tempVersionQuery").apply {
        isTransitive = false
        isCanBeResolved = true
    }
    project.repositories.apply { mavenCentral() }
    try {
        // Add dependency with "+" to query for latest version
        val queryDependency = project.dependencies.create("org.cs124.questioner:plugin:+")
        queryConfig.dependencies.add(queryDependency)

        // Resolve and return the version
        val resolved = queryConfig.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies
        return resolved.firstOrNull()?.moduleVersion
    } catch (_: Exception) {
        return null
    } finally {
        project.configurations.remove(queryConfig)
    }
}

fun isLatestVersion(): Boolean {
    val currentVersion = VERSION
    val latestVersion = getLatestQuestionerVersion()
    check(latestVersion != null) { "Unable to determine latest Questioner version" }
    return currentVersion.toVersion() >= latestVersion.toVersion()
}
