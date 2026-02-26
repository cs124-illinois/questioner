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
    val sourceRoot: File,
    val external: String? = null,
)

/**
 * Lightweight extraction of @Correct annotation fields.
 * Only extracts author, path, and name - doesn't do full ANTLR parsing.
 * This is used during the settings phase where we need fast discovery.
 */
fun File.extractCorrectInfo(sourceRoot: File, external: String? = null): DiscoveredQuestion? {
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
        sourceRoot = sourceRoot,
        external = external,
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
fun discoverQuestions(sourceDir: File, external: String? = null): List<DiscoveredQuestion> {
    if (!sourceDir.exists()) return emptyList()

    return sourceDir.walkTopDown()
        .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
        .mapNotNull { it.extractCorrectInfo(sourceDir, external) }
        .toList()
}

/**
 * Check a list of discovered questions for hash collisions.
 * Returns the list if no collisions, throws otherwise.
 */
fun checkForCollisions(questions: List<DiscoveredQuestion>): List<DiscoveredQuestion> {
    val byHash = questions.groupBy { it.hash }
    val collisions = byHash.filter { it.value.size > 1 }

    if (collisions.isNotEmpty()) {
        val message = buildString {
            appendLine("Detected question slug collisions:")
            collisions.forEach { (hash, dupes) ->
                appendLine("  Hash $hash (${dupes.first().fullSlug}):")
                dupes.forEach { q ->
                    appendLine("    - ${q.correctFile.absolutePath} (source root: ${q.sourceRoot.absolutePath})")
                }
            }
        }
        throw IllegalStateException(message)
    }

    return questions
}

/**
 * Discover questions from a single directory and check for collisions.
 */
fun discoverQuestionsWithCollisionCheck(sourceDir: File): List<DiscoveredQuestion> = checkForCollisions(discoverQuestions(sourceDir))

/**
 * Discover questions from multiple source directories and check for collisions across all of them.
 * Each entry is a pair of (sourceDir, externalSlug) where externalSlug is null for the primary source dir.
 */
fun discoverQuestionsFromDirs(sourceDirs: List<Pair<File, String?>>): List<DiscoveredQuestion> =
    checkForCollisions(sourceDirs.flatMap { (dir, external) -> discoverQuestions(dir, external) })
