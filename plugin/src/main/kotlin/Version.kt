package edu.illinois.cs.cs125.questioner.plugin

import com.beust.klaxon.Klaxon
import com.beust.klaxon.PathMatcher
import edu.illinois.cs.cs125.questioner.lib.VERSION
import io.github.z4kn4fein.semver.toVersion
import java.io.StringReader
import java.net.URI

private val pattern = "\$.response.docs[0].latestVersion"

fun getLatestQuestionerVersion(): String {
    var version = ""
    try {
        val url = "https://search.maven.org/solrsearch/select?q=g:org.cs124.questioner+AND+a:plugin&wt=json"
        val response = URI(url).toURL().readText()
        Klaxon().pathMatcher(object : PathMatcher {
            override fun pathMatches(path: String) = path == pattern
            override fun onMatch(path: String, value: Any) {
                version = value as String
            }
        }).parseJsonObject(StringReader(response))
    } catch (_: Exception) {
    }
    return version
}

fun isLatestVersion(): Boolean {
    val currentVersion = VERSION
    val latestVersion = getLatestQuestionerVersion()
    check(latestVersion != "") { "Unable to determine latest Questioner version" }
    return currentVersion.toVersion() >= latestVersion.toVersion()
}
