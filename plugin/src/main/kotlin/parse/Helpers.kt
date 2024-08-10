package edu.illinois.cs.cs125.questioner.plugin.parse

import edu.illinois.cs.cs125.jenisol.core.Both
import edu.illinois.cs.cs125.jenisol.core.Compare
import edu.illinois.cs.cs125.jenisol.core.Configure
import edu.illinois.cs.cs125.jenisol.core.DesignOnly
import edu.illinois.cs.cs125.jenisol.core.EdgeType
import edu.illinois.cs.cs125.jenisol.core.FilterParameters
import edu.illinois.cs.cs125.jenisol.core.FixedParameters
import edu.illinois.cs.cs125.jenisol.core.InstanceValidator
import edu.illinois.cs.cs125.jenisol.core.KotlinMirrorOK
import edu.illinois.cs.cs125.jenisol.core.Limit
import edu.illinois.cs.cs125.jenisol.core.NotNull
import edu.illinois.cs.cs125.jenisol.core.ProvideFileSystem
import edu.illinois.cs.cs125.jenisol.core.ProvideSystemIn
import edu.illinois.cs.cs125.jenisol.core.RandomParameters
import edu.illinois.cs.cs125.jenisol.core.RandomType
import edu.illinois.cs.cs125.jenisol.core.SimpleType
import edu.illinois.cs.cs125.jenisol.core.Verify
import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect
import edu.illinois.cs.cs125.questioner.lib.Blacklist
import edu.illinois.cs.cs125.questioner.lib.CheckFeatures
import edu.illinois.cs.cs125.questioner.lib.Cite
import edu.illinois.cs.cs125.questioner.lib.Correct
import edu.illinois.cs.cs125.questioner.lib.Ignore
import edu.illinois.cs.cs125.questioner.lib.Incorrect
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.Starter
import edu.illinois.cs.cs125.questioner.lib.Tags
import edu.illinois.cs.cs125.questioner.lib.TemplateImports
import edu.illinois.cs.cs125.questioner.lib.Whitelist
import edu.illinois.cs.cs125.questioner.lib.Wrap
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

internal val markdownParser = MarkdownParser(CommonMarkFlavourDescriptor())

internal data class CleanSpec(
    val hasTemplate: Boolean = false,
    val wrappedClass: String? = null,
    val importNames: Set<String> = setOf(),
) {
    val notClass = hasTemplate || wrappedClass != null
}

internal fun String.stripPackage(): String {
    val packageLine = lines().indexOfFirst { it.trim().startsWith("package ") }
    if (packageLine == -1) {
        return this
    }
    val range = if (packageLine != 0 && lines()[packageLine - 1].isBlank()) {
        (packageLine - 1)..packageLine
    } else {
        packageLine..packageLine
    }
    return lines().filterIndexed { index, _ -> !range.contains(index) }.joinToString("\n").trimStart()
}

internal val annotationsToRemove =
    setOf(
        Correct::class.java.simpleName,
        Tags::class.java.simpleName,
        Incorrect::class.java.simpleName,
        Starter::class.java.simpleName,
        SuppressWarnings::class.java.simpleName,
        Suppress::class.java.simpleName,
        Override::class.java.simpleName,
        Whitelist::class.java.simpleName,
        Blacklist::class.java.simpleName,
        TemplateImports::class.java.simpleName,
        DesignOnly::class.java.simpleName,
        Wrap::class.java.simpleName,
        AlsoCorrect::class.java.simpleName,
        Configure::class.java.simpleName,
        Cite::class.java.simpleName,
        Limit::class.java.simpleName,
        ProvideSystemIn::class.java.simpleName,
        ProvideFileSystem::class.java.simpleName,
        KotlinMirrorOK::class.java.simpleName,
    )
internal val annotationsToDestroy =
    setOf(
        FixedParameters::class.java.simpleName,
        RandomParameters::class.java.simpleName,
        Verify::class.java.simpleName,
        Both::class.java.simpleName,
        FilterParameters::class.java.simpleName,
        SimpleType::class.java.simpleName,
        EdgeType::class.java.simpleName,
        RandomType::class.java.simpleName,
        InstanceValidator::class.java.simpleName,
        CheckFeatures::class.java.simpleName,
        Ignore::class.java.simpleName,
        Compare::class.java.simpleName,
    )
internal val annotationsToSnip = setOf(NotNull::class.java.simpleName)

internal val controlAnnotations =
    setOf(
        Tags::class.java.simpleName,
        Whitelist::class.java.simpleName,
        Blacklist::class.java.simpleName,
        TemplateImports::class.java.simpleName,
        DesignOnly::class.java.simpleName,
        Wrap::class.java.simpleName,
        Configure::class.java.simpleName,
        Cite::class.java.simpleName,
        Limit::class.java.simpleName,
        ProvideSystemIn::class.java.simpleName,
        ProvideFileSystem::class.java.simpleName,
        KotlinMirrorOK::class.java.simpleName,
        Verify::class.java.simpleName,
        Both::class.java.simpleName,
        FilterParameters::class.java.simpleName,
        SimpleType::class.java.simpleName,
        EdgeType::class.java.simpleName,
        RandomType::class.java.simpleName,
        InstanceValidator::class.java.simpleName,
        CheckFeatures::class.java.simpleName,
        Ignore::class.java.simpleName,
        Compare::class.java.simpleName,
    )
internal val controlImports = controlAnnotations.map { "edu.illinois.cs.cs125.questioner.lib.$it" }.toSet()

internal val importsToRemove = annotationsToRemove.map { "edu.illinois.cs.cs125.questioner.lib.$it" }.toSet() +
    "edu.illinois.cs.cs125.questioner.lib.Ignore"
internal val packagesToRemove = setOf("edu.illinois.cs.cs125.jenisol")

@Suppress("SpellCheckingInspection")
internal fun String.toReason() = when (uppercase()) {
    "DESIGN" -> Question.IncorrectFile.Reason.DESIGN
    "TEST" -> Question.IncorrectFile.Reason.TEST
    "COMPILE" -> Question.IncorrectFile.Reason.COMPILE
    "CHECKSTYLE" -> Question.IncorrectFile.Reason.CHECKSTYLE
    "TIMEOUT" -> Question.IncorrectFile.Reason.TIMEOUT
    "DEADCODE" -> Question.IncorrectFile.Reason.DEADCODE
    "LINECOUNT" -> Question.IncorrectFile.Reason.LINECOUNT
    "TOOLONG" -> Question.IncorrectFile.Reason.TOOLONG
    "MEMORYLIMIT" -> Question.IncorrectFile.Reason.MEMORYLIMIT
    "RECURSION" -> Question.IncorrectFile.Reason.RECURSION
    "COMPLEXITY" -> Question.IncorrectFile.Reason.COMPLEXITY
    "FEATURES" -> Question.IncorrectFile.Reason.FEATURES
    "MEMOIZATION" -> Question.IncorrectFile.Reason.MEMOIZATION
    "CLASSSIZE" -> Question.IncorrectFile.Reason.CLASSSIZE
    "KTLINT" -> Question.IncorrectFile.Reason.KTLINT
    "EXTRAOUTPUT" -> Question.IncorrectFile.Reason.EXTRAOUTPUT
    else -> error("Invalid incorrect reason: $this")
}
