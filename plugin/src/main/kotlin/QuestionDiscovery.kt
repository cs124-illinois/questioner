package edu.illinois.cs.cs125.questioner.plugin

import com.github.slugify.Slugify
import java.io.File
import java.security.MessageDigest

private val slugify = Slugify.builder().build()

/**
 * Represents a discovered question from the filesystem.
 * Used during settings phase to create subprojects.
 */
data class DiscoveredQuestion(
    val correctFile: File,
    val author: String,
    val slug: String,
    val fullSlug: String,
    val hash: String,
)

/**
 * Lightweight extraction of @Correct annotation fields.
 * Only extracts author, path, and name - doesn't do full ANTLR parsing.
 * This is used during the settings phase where we need fast discovery.
 */
fun File.extractCorrectInfo(): DiscoveredQuestion? {
    val content = readText()

    // Quick check - does this file have @Correct?
    if (!content.contains("@Correct")) return null

    // Make sure it's the annotation, not just a comment or string
    // Look for @Correct followed by ( with optional whitespace
    val annotationRegex = """@Correct\s*\(([^)]+)\)""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val match = annotationRegex.find(content) ?: return null
    val params = match.groupValues[1]

    fun extractParam(name: String): String? {
        val paramRegex = """$name\s*=\s*"([^"]+)"""".toRegex()
        return paramRegex.find(params)?.groupValues?.get(1)
    }

    val author = extractParam("author") ?: return null
    val name = extractParam("name") ?: return null
    val path = extractParam("path")

    val slug = path ?: slugify.slugify(name)
    val fullSlug = "$author/$slug"
    val hash = fullSlug.sha256Take16()

    return DiscoveredQuestion(
        correctFile = this,
        author = author,
        slug = slug,
        fullSlug = fullSlug,
        hash = hash,
    )
}

/**
 * Compute SHA-256 hash and take first 16 hex characters.
 */
fun String.sha256Take16(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.take(8).joinToString("") { "%02x".format(it) }
}

/**
 * Discover all @Correct files in a source directory.
 */
fun discoverQuestions(sourceDir: File): List<DiscoveredQuestion> {
    if (!sourceDir.exists()) return emptyList()

    return sourceDir.walkTopDown()
        .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
        .mapNotNull { it.extractCorrectInfo() }
        .toList()
}

/**
 * Discover questions and check for collisions.
 * Returns the list of questions or throws if collisions are detected.
 */
fun discoverQuestionsWithCollisionCheck(sourceDir: File): List<DiscoveredQuestion> {
    val questions = discoverQuestions(sourceDir)

    // Check for hash collisions (same author/slug from different locations)
    val byHash = questions.groupBy { it.hash }
    val collisions = byHash.filter { it.value.size > 1 }

    if (collisions.isNotEmpty()) {
        val message = buildString {
            appendLine("Detected question slug collisions:")
            collisions.forEach { (hash, questions) ->
                appendLine("  Hash $hash (${questions.first().fullSlug}):")
                questions.forEach { q ->
                    appendLine("    - ${q.correctFile.absolutePath}")
                }
            }
        }
        throw IllegalStateException(message)
    }

    return questions
}
