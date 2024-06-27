package edu.illinois.cs.cs125.questioner.plugin

import com.beust.klaxon.Klaxon
import com.beust.klaxon.PathMatcher
import edu.illinois.cs.cs125.questioner.lib.VERSION
import io.github.z4kn4fein.semver.toVersion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import java.io.StringReader

private val pattern = "\$.response.docs[0].latestVersion"

suspend fun getLatestQuestionerVersion(): String {
    var version = ""
    try {
        val client = HttpClient(CIO)
        val response =
            client.get("https://search.maven.org/solrsearch/select?q=g:org.cs124.questioner+AND+a:plugin&wt=json")
        Klaxon().pathMatcher(object : PathMatcher {
            override fun pathMatches(path: String) = path == pattern
            override fun onMatch(path: String, value: Any) {
                version = value as String
            }
        }).parseJsonObject(StringReader(response.body()))
    } catch (_: Exception) {
    }
    return version
}

suspend fun isLatestVersion(): Boolean {
    val currentVersion = VERSION
    val latestVersion = getLatestQuestionerVersion()
    check(latestVersion != "") { "Unable to determine latest Questioner version" }
    return currentVersion.toVersion() >= latestVersion.toVersion()
}
