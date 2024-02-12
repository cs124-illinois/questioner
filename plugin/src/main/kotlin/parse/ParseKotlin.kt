package edu.illinois.cs.cs125.questioner.plugin.parse

import edu.illinois.cs.cs125.jeed.core.KtLintArguments
import edu.illinois.cs.cs125.jeed.core.SnippetArguments
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.complexity
import edu.illinois.cs.cs125.jeed.core.countLines
import edu.illinois.cs.cs125.jeed.core.features
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.ktFormat
import edu.illinois.cs.cs125.questioner.antlr.KotlinLexer
import edu.illinois.cs.cs125.questioner.antlr.KotlinParser
import edu.illinois.cs.cs125.questioner.antlr.KotlinParser.ImportHeaderContext
import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect
import edu.illinois.cs.cs125.questioner.lib.Correct
import edu.illinois.cs.cs125.questioner.lib.Incorrect
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.Starter
import io.kotest.common.runBlocking
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.apache.tools.ant.filters.StringInputStream
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import java.io.File

internal data class ParsedKotlinFile(val path: String, val contents: String) {
    constructor(file: File) : this(file.path, file.readText().replace("\r\n", "\n"))

    init {
        require(path.endsWith(".kt")) { "Can only parse Kotlin files" }
    }

    private val parsedSource = contents.parseKotlin()
    private val parseTree = parsedSource.tree

    val topLevelFile = parseTree
        .preamble()
        .fileAnnotations()
        ?.getAnnotation(AlsoCorrect::class.java, Incorrect::class.java, Starter::class.java) != null

    private val topLevelClass = if (!topLevelFile) {
        parseTree.topLevelObject()
            .filter { it.classDeclaration() != null }
            .also {
                require(it.size == 1) { "Kotlin files must only contain a single top-level class declaration: ${it.size}" }
            }.first().classDeclaration()
    } else {
        null
    }

    private val alternateSolution = when (topLevelFile) {
        true -> parseTree.preamble().fileAnnotations().getAnnotation(AlsoCorrect::class.java)
        false -> topLevelClass!!.getAnnotation(AlsoCorrect::class.java)
    }
    val isAlternateSolution = alternateSolution != null

    private val starter = when (topLevelFile) {
        true -> parseTree.preamble().fileAnnotations().getAnnotation(Starter::class.java)
        false -> topLevelClass!!.getAnnotation(Starter::class.java)
    }
    val isStarter = starter != null

    val isCorrect = when (topLevelFile) {
        true -> parseTree.preamble().fileAnnotations().getAnnotation(Correct::class.java) != null
        false -> topLevelClass!!.getAnnotation(Correct::class.java) != null
    }

    val hasControlAnnotations =
        parseTree.preamble().importList().importHeader().map { it.toFullName() }.toSet().intersect(controlImports)
            .isNotEmpty()

    val suppressions = when (topLevelFile) {
        true -> parseTree.preamble().fileAnnotations().getAnnotation(Suppress::class.java)
            ?.valueArguments()?.valueArgument()
            ?.map { it.text.trim().removeSurrounding("\"") }
            ?.toSet()
            ?: setOf()

        false -> topLevelClass!!.getAnnotation(Suppress::class.java)
            ?.valueArguments()?.valueArgument()
            ?.map { it.text.trim().removeSurrounding("\"") }
            ?.toSet()
            ?: setOf()
    }

    val incorrect = if (topLevelFile) {
        parseTree.preamble().fileAnnotations().getAnnotation(Incorrect::class.java)
    } else {
        topLevelClass!!.getAnnotation(Incorrect::class.java)
    }?.let { ruleContext ->
        when (ruleContext) {
            is KotlinParser.AnnotationContext -> ruleContext.valueArguments()
            is KotlinParser.UnescapedAnnotationContext -> ruleContext.valueArguments()
            else -> error("Bad annotation chain")
        }?.valueArgument()?.let {
            check(it.size == 1) { "Invalid @Incorrect annotation" }
            it.first()
        }?.let { content ->
            content.simpleIdentifier()?.text?.let {
                check(it == "reason") { "Invalid @Incorrect annotation" }
            }
            content.expression()?.text?.removeSurrounding("\"")
        } ?: "test"
    }
    val isIncorrect = incorrect != null

    val isQuestioner = isAlternateSolution || isStarter || isIncorrect

    fun toIncorrectFile(cleanSpec: CleanSpec): Question.IncorrectFile {
        check(incorrect != null) { "Not an incorrect file" }
        return Question.IncorrectFile(
            className,
            clean(cleanSpec).trimStart(),
            incorrect.toReason(),
            Language.kotlin,
            path,
            starter != null,
            suppressions = suppressions,
        )
    }

    val className: String = if (topLevelFile) {
        "${File(path).nameWithoutExtension}Kt"
    } else {
        topLevelClass!!.simpleIdentifier().text
    }

    fun toAlternateFile(cleanSpec: CleanSpec): Question.FlatFile = runBlocking {
        check(alternateSolution != null) { "Not an alternate solution file" }
        val solutionContentWithDead = clean(cleanSpec).trimStart()
        val deadlineCount = solutionContentWithDead.lines().filter {
            it.trimEnd().endsWith("// dead code")
        }.size
        val unformattedContent = solutionContentWithDead.lines().joinToString("\n") {
            it.trimEnd().removeSuffix("// dead code").trimEnd()
        }
        val solutionContent = try {
            Source.fromKotlin(unformattedContent).ktFormat(
                KtLintArguments(indent = 2, maxLineLength = 120),
            ).contents
        } catch (_: Exception) {
            Source.fromKotlin(unformattedContent)
                .ktFormat(KtLintArguments(script = true, indent = 2, maxLineLength = 120)).contents
        }
        val source = if (cleanSpec.notClass) {
            Source.fromSnippet(solutionContent, SnippetArguments(fileType = Source.FileType.KOTLIN, noEmptyMain = true))
        } else {
            Source(mapOf("$className.kt" to solutionContent))
        }
        val complexity = source.complexity().let {
            if (cleanSpec.notClass) {
                it.lookup("").complexity
            } else {
                it.lookupFile("$className.kt")
            }
        }.also {
            // Kotlin class declarations with implicit getters can have zero complexity
            check(it >= 0) { "Invalid complexity value" }
        }
        val lineCounts = solutionContent.countLines(Source.FileType.KOTLIN)
        val features = source.features().let { features ->
            if (cleanSpec.notClass) {
                features.lookup("")
            } else {
                features.lookup("", "$className.kt")
            }
        }.features
        return@runBlocking Question.FlatFile(
            className,
            solutionContent,
            Language.kotlin,
            path,
            complexity,
            features,
            lineCounts,
            deadlineCount,
            suppressions = suppressions,
        )
    }

    fun toStarterFile(cleanSpec: CleanSpec): Question.IncorrectFile {
        check(starter != null) { "Not an starter code file" }
        return Question.IncorrectFile(
            className,
            clean(cleanSpec).trimStart(),
            incorrect?.toReason() ?: "test".toReason(),
            Language.kotlin,
            path,
            true,
            suppressions = suppressions,
        )
    }

    private fun ImportHeaderContext.toFullName() = identifier().text + if (MULT() != null) {
        ".*"
    } else {
        ""
    }

    init {
        parseTree.preamble().importList().importHeader().map { it.toFullName() }.also { imports ->
            imports.filter { it.endsWith(".NotNull") || it.endsWith(".NonNull") }
                .also {
                    require(it.isEmpty()) {
                        "@NotNull or @NonNull annotations will not be applied when used in Kotlin solutions"
                    }
                }
        }
    }

    @Suppress("unused")
    private fun removeImports(importNames: List<String>): String {
        val toRemove = mutableSetOf<Int>()
        parseTree.preamble().importList().importHeader().forEach { importHeaderContext ->
            val packageName = importHeaderContext.toFullName()
            if (packageName in importNames) {
                toRemove.add(importHeaderContext.start.startIndex.toLine())
            }
        }
        return contents
            .split("\n")
            .filterIndexed { index, _ -> (index + 1) !in toRemove }
            .joinToString("\n")
            .trim()
    }

    private val chars = contents.toCharArray()
    private fun Int.toLine(): Int {
        var lines = 1
        for (i in 0 until this) {
            if (chars[i] == '\n') {
                lines++
            }
        }
        return lines
    }

    val comment = if (topLevelFile) {
        parseTree.comment()
    } else {
        topLevelClass!!.comment()
    }

    val description =
        if (comment != null) {
            markdownParser.buildMarkdownTreeFromString(comment).let { astNode ->
                HtmlGenerator(comment, astNode, CommonMarkFlavourDescriptor()).generateHtml()
                    .removeSurrounding("<body>", "</body>")
            }
        } else {
            null
        }

    @Suppress("ComplexMethod")
    private fun clean(cleanSpec: CleanSpec): String {
        val (hasTemplate, wrapWith, importNames) = cleanSpec
        val toRemove = mutableSetOf<Int>()
        parseTree.preamble()?.packageHeader()?.also {
            toRemove.add(it.start.startIndex.toLine())
        }
        parseTree.preamble().importList().importHeader().forEach { importHeaderContext ->
            val packageName = importHeaderContext.toFullName()
            if (packageName in importsToRemove ||
                packageName in importNames ||
                packagesToRemove.any { packageName.startsWith(it) }
            ) {
                toRemove.add(importHeaderContext.start.startIndex.toLine())
            }
        }
        if (topLevelFile) {
            parseTree.topLevelObject().forEach { topLevelObject ->
                topLevelObject.DelimitedComment()?.also { node ->
                    (node.symbol.startIndex.toLine()..node.symbol.stopIndex.toLine()).forEach { toRemove.add(it) }
                }
            }
            parseTree.preamble().fileAnnotations().fileAnnotation()?.flatMap { it.unescapedAnnotation() }
                ?.filter { annotation ->
                    annotation.identifier()?.text != null &&
                        annotationsToRemove.contains(annotation.identifier().text.removePrefix("@"))
                }?.forEach { context ->
                    (context.start.startIndex.toLine()..context.stop.stopIndex.toLine()).forEach {
                        toRemove.add(it)
                    }
                }
            parseTree.topLevelObject().mapNotNull { it.functionDeclaration()?.modifierList()?.annotations() }
                .flatten().mapNotNull { it.annotation() }.filter { annotation ->
                    annotation.LabelReference()?.text != null &&
                        annotationsToRemove.contains(annotation.LabelReference().text.removePrefix("@"))
                }.forEach { context ->
                    (context.start.startIndex.toLine()..context.stop.stopIndex.toLine()).forEach {
                        toRemove.add(it)
                    }
                }
        } else {
            topLevelClass!!.DelimitedComment()?.also { node ->
                (node.symbol.startIndex.toLine()..node.symbol.stopIndex.toLine()).forEach { toRemove.add(it) }
            }
            parseTree.preamble().fileAnnotations()?.fileAnnotation()?.flatMap { it.unescapedAnnotation() }
                ?.filter { annotation ->
                    annotation.identifier()?.text != null &&
                        annotationsToRemove.contains(annotation.identifier().text.removePrefix("@"))
                }?.forEach { context ->
                    (context.start.startIndex.toLine()..context.stop.stopIndex.toLine()).forEach {
                        toRemove.add(it)
                    }
                }
            topLevelClass.modifierList().annotations()
                ?.filter {
                    it.annotation().LabelReference()?.text != null && annotationsToRemove.contains(
                        it.annotation().LabelReference().text.removePrefix("@"),
                    )
                }?.forEach { context ->
                    (context.start.startIndex.toLine()..context.stop.stopIndex.toLine()).forEach {
                        toRemove.add(it)
                    }
                }
        }

        return contents
            .split("\n")
            .filterIndexed { index, _ -> (index + 1) !in toRemove }
            .joinToString("\n") { line ->
                Regex("""//.*mutate-disable""").find(line)?.let {
                    line.substring(0 until it.range.first).trimEnd()
                } ?: line
            }
            .trim()
            .kotlinDeTemplate(hasTemplate, wrapWith)
            .stripPackage()
    }

    var usedImports: List<String> = listOf()

    fun extractTemplate(importNames: Set<String>): String? {
        val correctSolution = clean(CleanSpec(false, null, importNames)).trimStart()
        val templateStart = Regex("""//.*TEMPLATE_START""").find(correctSolution)?.range?.start ?: return null
        val templateEnd = correctSolution.indexOf("TEMPLATE_END")
        val start = correctSolution.substring(0 until templateStart)
        val end = correctSolution.substring((templateEnd + "TEMPLATE_END".length) until correctSolution.length)
        return "$start{{{ contents }}}$end"
    }

    fun extractStarter(wrappedClass: String?): Question.IncorrectFile? {
        val correctSolution = clean(CleanSpec(false, null)).trimStart()
        val parsed = correctSolution.parseKotlin()
        val methodDeclaration = if (topLevelFile) {
            parsed.tree.topLevelObject(0)?.functionDeclaration()
        } else {
            parsed.tree.topLevelObject(0)?.classDeclaration()?.classBody()?.classMemberDeclaration(0)
                ?.functionDeclaration()
        } ?: return null

        val start = methodDeclaration.functionBody().start.startIndex
        val end = methodDeclaration.functionBody().stop.stopIndex
        val returnType = methodDeclaration.type().let {
            if (it.isEmpty()) {
                "Unit"
            } else {
                check(it.last().start.startIndex > methodDeclaration.identifier().start.startIndex) {
                    "Couldn't find method return type"
                }
                it.last().text
            }
        }
        val arrayRegex = """Array<(\w+)>""".toRegex()
        val starterReturn = when {
            returnType == "Unit" -> ""
            returnType.endsWith("?") -> "null"
            returnType == "Byte" -> "0"
            returnType == "Short" -> "0"
            returnType == "Int" -> "0"
            returnType == "Long" -> "0"
            returnType == "Float" -> "0.0"
            returnType == "Double" -> "0.0"
            returnType == "Char" -> "' '"
            returnType == "Boolean" -> "false"
            returnType == "String" -> "\"\""
            returnType == "ByteArray" -> "byteArrayOf()"
            returnType == "ShortArray" -> "shortArrayOf()"
            returnType == "IntArray" -> "intArrayOf()"
            returnType == "LongArray" -> "longArrayOf()"
            returnType == "FloatArray" -> "floatArrayOf()"
            returnType == "DoubleArray" -> "doubleArrayOf()"
            returnType == "BooleanArray" -> "booleanArrayOf()"
            returnType == "CharArray" -> "charArrayOf()"
            returnType.matches(arrayRegex) -> arrayRegex.find(returnType)?.let {
                "arrayOf<${it.groups[1]!!.value}>()"
            } ?: error("regex only matched the first time")

            else -> error("Can't generate empty Kotlin return for type $returnType")
        }.let {
            if (it.isNotBlank()) {
                " $it"
            } else {
                it
            }
        }
        val prefix = (start + 1 until correctSolution.length).find { i -> !correctSolution[i].isWhitespace() }.let {
            check(it != null) { "Couldn't find method contents" }
            it - 1
        }
        val postfix = (end - 1 downTo start).find { i -> !correctSolution[i].isWhitespace() }.let {
            check(it != null) { "Couldn't find method contents" }
            it + 1
        }
        return (
            correctSolution.substring(0..prefix) +
                "return$starterReturn // You may need to remove this starter code" +
                correctSolution.substring(postfix until correctSolution.length)
            )
            .kotlinDeTemplate(false, wrappedClass).let {
                Question.IncorrectFile(
                    className,
                    it,
                    Question.IncorrectFile.Reason.TEST,
                    Language.kotlin,
                    null,
                    true,
                )
            }
    }

    private fun String.kotlinDeTemplate(hasTemplate: Boolean, wrappedClass: String?) = when {
        wrappedClass != null && topLevelFile -> {
            usedImports = parseKotlin().tree.preamble().importList().importHeader().map { it.toFullName() }
            this
        }

        wrappedClass != null && !topLevelFile -> parseKotlin().tree.also { context ->
            usedImports = context.preamble().importList().importHeader().map { it.toFullName() }
        }.topLevelObject()
            .filter { it.classDeclaration() != null }.also {
                require(it.size == 1) { "Kotlin files must only contain a single top-level class declaration: $this" }
            }.first().classDeclaration().let { context ->
                val start = context.start.line
                val end = context.stop.line
                split("\n").subList(start, end - 1).also { lines ->
                    require(
                        lines.find {
                            it.contains("TEMPLATE_START") || it.contains("TEMPLATE_END")
                        } == null,
                    ) {
                        "@Wrap should not use template delimiters"
                    }
                }.joinToString("\n").trimIndent().trim()
            }

        !hasTemplate -> this
        else -> {
            val lines = split("\n")
            val start = lines.indexOfFirst { it.contains("TEMPLATE_START") }
            val end = lines.indexOfFirst { it.contains("TEMPLATE_END") }
            require(start != -1) { "Couldn't locate TEMPLATE_START during extraction" }
            require(end != -1) { "Couldn't locate TEMPLATE_END during extraction" }
            lines.slice((start + 1) until end).joinToString(separator = "\n").trimIndent()
        }
    }
}

data class ParsedKotlinContent(val tree: KotlinParser.KotlinFileContext, val stream: CharStream)

internal fun String.parseKotlin() = CharStreams.fromStream(StringInputStream(this)).let { stream ->
    KotlinLexer(stream).also { it.removeErrorListeners() }.let { lexer ->
        CommonTokenStream(lexer).let { tokens ->
            KotlinParser(tokens).apply {
                // makeThreadSafe()
            }.also { parser ->
                parser.removeErrorListeners()
                parser.addErrorListener(
                    object : BaseErrorListener() {
                        override fun syntaxError(
                            recognizer: Recognizer<*, *>?,
                            offendingSymbol: Any?,
                            line: Int,
                            charPositionInLine: Int,
                            msg: String?,
                            e: RecognitionException?,
                        ) {
                            // Ignore messages that are not errors...
                            if (e == null) {
                                return
                            }
                            throw e
                        }
                    },
                )
            }
        }.kotlinFile()
    }.let { tree ->
        ParsedKotlinContent(tree, stream)
    }
}

fun KotlinParser.FileAnnotationsContext.getAnnotation(vararg toFind: Class<*>): KotlinParser.UnescapedAnnotationContext? =
    fileAnnotation()?.flatMap { it.unescapedAnnotation() }?.find { annotation ->
        annotation.identifier()?.text != null && toFind.map { it.simpleName }.contains(annotation.identifier().text)
    }

fun KotlinParser.FileAnnotationsContext.getAnnotation(vararg toFind: String): KotlinParser.UnescapedAnnotationContext? =
    fileAnnotation()?.flatMap { it.unescapedAnnotation() }?.find { annotation ->
        annotation.identifier()?.text != null && toFind.contains(annotation.identifier().text)
    }

fun KotlinParser.ClassDeclarationContext.getAnnotation(annotation: Class<*>): KotlinParser.AnnotationContext? =
    modifierList()?.annotations()?.find {
        it.annotation()?.LabelReference()?.text == "@${annotation.simpleName}"
    }?.annotation()

fun KotlinParser.ClassDeclarationContext.getAnnotation(vararg toFind: String): KotlinParser.AnnotationContext? =
    modifierList()?.annotations()?.find { toFind.contains(it.annotation()?.LabelReference()?.text) }?.annotation()

fun KotlinParser.KotlinFileContext.comment() =
    topLevelObject().find { it.DelimitedComment() != null }?.DelimitedComment()?.text
        ?.toString()?.split("\n")?.joinToString(separator = "\n") { line ->
            line.trim()
                .removePrefix("""/*""")
                .removePrefix("""*/""")
                .removePrefix("""* """)
                .removeSuffix("""*//*""")
                .let {
                    if (it == "*") {
                        ""
                    } else {
                        it
                    }
                }
        }?.trim()

fun KotlinParser.ClassDeclarationContext.comment() = DelimitedComment()?.text
    ?.toString()?.split("\n")?.joinToString(separator = "\n") { line ->
        line.trim()
            .removePrefix("""/*""")
            .removePrefix("""*/""")
            .removePrefix("""* """)
            .removeSuffix("""*//*""")
            .let {
                if (it == "*") {
                    ""
                } else {
                    it
                }
            }
    }?.trim()
