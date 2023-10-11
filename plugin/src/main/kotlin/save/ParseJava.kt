package edu.illinois.cs.cs125.questioner.plugin.save

import com.google.googlejavaformat.java.Formatter
import edu.illinois.cs.cs125.jeed.core.CheckstyleArguments
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.checkstyle
import edu.illinois.cs.cs125.jeed.core.complexity
import edu.illinois.cs.cs125.jeed.core.countLines
import edu.illinois.cs.cs125.jeed.core.features
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.googleFormat
import edu.illinois.cs.cs125.jenisol.core.NotNull
import edu.illinois.cs.cs125.questioner.antlr.JavaLexer
import edu.illinois.cs.cs125.questioner.antlr.JavaParser
import edu.illinois.cs.cs125.questioner.antlr.JavaParser.ElementValueContext
import edu.illinois.cs.cs125.questioner.antlr.JavaParser.ImportDeclarationContext
import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect
import edu.illinois.cs.cs125.questioner.lib.Blacklist
import edu.illinois.cs.cs125.questioner.lib.CheckstyleSuppress
import edu.illinois.cs.cs125.questioner.lib.Cite
import edu.illinois.cs.cs125.questioner.lib.Correct
import edu.illinois.cs.cs125.questioner.lib.Incorrect
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.Starter
import edu.illinois.cs.cs125.questioner.lib.TemplateImports
import edu.illinois.cs.cs125.questioner.lib.Whitelist
import edu.illinois.cs.cs125.questioner.lib.Wrap
import edu.illinois.cs.cs125.questioner.lib.toReason
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

data class ParsedJavaFile(val path: String, val contents: String) {
    constructor(file: File) : this(file.path, file.readText().replace("\r\n", "\n"))

    init {
        require(path.endsWith(".java")) { "Can only parse Java files" }
    }

    private val parsedSource = contents.parseJava()
    private val parseTree = parsedSource.tree
    private val topLevelClass = parseTree.topLevelClass()

    val packageName = parseTree.packageName()
    val className = parseTree.className()

    val fullName = "$packageName.$className"

    private fun ImportDeclarationContext.toFullName() = qualifiedName().asString() + if (MUL() != null) {
        ".*"
    } else {
        ""
    }

    val listedImports = parseTree.importDeclaration()
        .map { it.toFullName() }
        .filter { it !in importsToRemove }
        .also { imports ->
            imports.filter { it.endsWith(".NotNull") || it.endsWith(".NonNull") }
                .filter { it != NotNull::class.java.name }
                .also {
                    require(it.isEmpty()) {
                        "Please use the Questioner @NotNull annotation from ${NotNull::class.java.name}"
                    }
                }
        }

    val whitelist = (
        topLevelClass.getAnnotations(Whitelist::class.java).let { annotations ->
            check(annotations.size <= 1) { "Found multiple @Whitelist annotations" }
            if (annotations.isEmpty()) {
                listOf()
            } else {
                annotations.first().let { annotation ->
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        annotation.parameterMap().let { it["paths"] ?: error("path field not set on @Whitelist") }
                    } catch (e: Exception) {
                        error("Couldn't parse @Whitelist paths for $path: $e")
                    }.let { names ->
                        names.split(",").map { it.trim() }
                    }
                }
            }
        } + topLevelClass.getAnnotations(TemplateImports::class.java).let { annotations ->
            check(annotations.size <= 1) { "Found multiple @TemplateImports annotations" }
            if (annotations.isEmpty()) {
                listOf()
            } else {
                annotations.first().let { annotation ->
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        annotation.parameterMap().let { it["paths"] ?: error("path field not set on @TemplateImports") }
                    } catch (e: Exception) {
                        error("Couldn't parse @TemplateImports paths for $path: $e")
                    }.let { names ->
                        names.split(",").map { it.trim() }
                    }
                }
            }
        }
        ).toSet()

    val blacklist = topLevelClass.getAnnotations(Blacklist::class.java).let { annotations ->
        check(annotations.size <= 1) { "Found multiple @Blacklist annotations" }
        if (annotations.isEmpty()) {
            listOf()
        } else {
            annotations.first().let { annotation ->
                @Suppress("TooGenericExceptionCaught")
                try {
                    annotation.parameterMap().let { it["paths"] ?: error("path field not set on @Blacklist") }
                } catch (e: Exception) {
                    error("Couldn't parse @Blacklist paths for $path: $e")
                }.let { names ->
                    names.split(",").map { it.trim() }
                }
            }
        }
    }.toSet()

    val checkstyleSuppress = topLevelClass.getAnnotations(CheckstyleSuppress::class.java).let { annotations ->
        check(annotations.size <= 1) { "Found multiple @CheckstyleSuppress annotations" }
        if (annotations.isEmpty()) {
            listOf()
        } else {
            annotations.first().let { annotation ->
                @Suppress("TooGenericExceptionCaught")
                try {
                    annotation.parameterMap()
                        .let { it["suppressions"] ?: error("suppressions field not set on @CheckstyleSuppress") }
                } catch (e: Exception) {
                    error("Couldn't parse @CheckstyleSuppress suppressions for $path: $e")
                }.let { names ->
                    names.split(",").map { it.trim() }
                }
            }
        }
    }.toSet()

    val hasTemplateImports = topLevelClass.getAnnotation(TemplateImports::class.java) != null

    val templateImports = topLevelClass.getAnnotations(TemplateImports::class.java).let { annotations ->
        check(annotations.size <= 1) { "Found multiple @TemplateImports annotations" }
        if (annotations.isEmpty()) {
            listOf()
        } else {
            annotations.first().let { annotation ->
                @Suppress("TooGenericExceptionCaught")
                try {
                    annotation.parameterMap().let { it["paths"] ?: error("path field not set on @TemplateImports") }
                } catch (e: Exception) {
                    error("Couldn't parse @Blacklist paths for $path: $e")
                }.let { names ->
                    names.split(",").map { it.trim() }.map {
                        check(!it.endsWith("*")) { "Wildcard imports not allowed in @TemplateImports" }
                        it
                    }
                }
            }
        }
    }

    val correct = topLevelClass.getAnnotation(Correct::class.java)?.let { annotation ->
        @Suppress("TooGenericExceptionCaught")
        try {
            annotation.parameterMap().let { parameters ->
                val path = parameters["path"]
                val name = parameters["name"] ?: error("name field not set on @Correct")
                val version = parameters["version"] ?: error("version field not set on @Correct")
                val author = parameters["author"] ?: ""
                if (author !== "") {
                    check(author.isEmail()) { "author field is not an email address" }
                }
                val description = annotation.comment().let { comment ->
                    markdownParser.buildMarkdownTreeFromString(comment).let { astNode ->
                        HtmlGenerator(comment, astNode, CommonMarkFlavourDescriptor()).generateHtml()
                            .removeSurrounding("<body>", "</body>")
                    }
                }
                val focused = parameters["focused"]?.toBoolean() ?: Question.Metadata.DEFAULT_FOCUSED
                val publish = parameters["publish"]?.toBoolean() ?: Question.Metadata.DEFAULT_PUBLISH

                val solutionThrows = parameters["solutionThrows"]?.toBoolean()
                val minTestCount = parameters["minTestCount"]?.toInt()
                val maxTestCount = parameters["maxTestCount"]?.toInt()
                val minTimeout = parameters["minTimeout"]?.toInt()
                val maxTimeout = parameters["maxTimeout"]?.toInt()
                val timeoutMultiplier = parameters["timeoutMultiplier"]?.toInt()
                val minMutationCount = parameters["minMutationCount"]?.toInt()
                val maxMutationCount = parameters["maxMutationCount"]?.toInt()
                val outputMultiplier = parameters["outputMultiplier"]?.toInt()
                val maxExtraComplexity = parameters["maxExtraComplexity"]?.toInt()
                val maxDeadCode = parameters["maxDeadCode"]?.toInt()
                val maxExecutionCountMultiplier = parameters["maxExecutionCountMultiplier"]?.toLong()
                val executionCountFailureMultiplier = parameters["executionCountFailureMultiplier"]?.toInt()
                val executionCountTimeoutMultiplier = parameters["executionCountTimeoutMultiplier"]?.toInt()
                val allocationFailureMultiplier = parameters["allocationFailureMultiplier"]?.toDouble()
                val allocationLimitMultiplier = parameters["allocationLimitMultiplier"]?.toDouble()
                val minExtraSourceLines = parameters["minExtraSourceLines"]?.toInt()
                val sourceLinesMultiplier = parameters["sourceLinesMultiplier"]?.toDouble()
                val seed = parameters["seed"]?.toInt()
                val maxComplexityMultiplier = parameters["maxComplexityMultiplier"]?.toInt()
                val maxLineCountMultiplier = parameters["maxLineCountMultiplier"]?.toInt()
                val maxClassSizeMultiplier = parameters["maxClassSizeMultiplier"]?.toInt()

                Question.CorrectData(
                    path,
                    name,
                    version,
                    author,
                    description,
                    focused,
                    publish,
                    Question.TestingControl(
                        solutionThrows,
                        minTestCount,
                        maxTestCount,
                        minTimeout,
                        maxTimeout,
                        timeoutMultiplier,
                        minMutationCount,
                        maxMutationCount,
                        outputMultiplier,
                        maxExtraComplexity,
                        maxDeadCode,
                        maxExecutionCountMultiplier,
                        executionCountFailureMultiplier,
                        executionCountTimeoutMultiplier,
                        allocationFailureMultiplier,
                        allocationLimitMultiplier,
                        minExtraSourceLines,
                        sourceLinesMultiplier,
                        seed,
                        maxComplexityMultiplier,
                        maxLineCountMultiplier,
                        maxClassSizeMultiplier,
                    ),
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            error("Couldn't parse @Correct metadata for $path: $e")
        }
    }

    val starter = topLevelClass.getAnnotation(Starter::class.java)

    val incorrect = topLevelClass.getAnnotation(Incorrect::class.java)?.let { annotation ->
        @Suppress("TooGenericExceptionCaught")
        try {
            annotation.parameterMap().let { parameters ->
                parameters["reason"] ?: parameters["value"] ?: "test"
            }
        } catch (e: Exception) {
            error("Couldn't parse @Incorrect metadata for $path: $e")
        }
    }

    val alternateSolution = topLevelClass.getAnnotation(AlsoCorrect::class.java)

    val type = mutableListOf<String>().also {
        if (correct != null) {
            it.add(Correct::class.java.simpleName)
        }
        if (starter != null) {
            it.add(Starter::class.java.simpleName)
        }
        if (incorrect != null) {
            it.add(Incorrect::class.java.simpleName)
        }
    }.also { types ->
        require(types.size < 2 || (types.size == 2 && types.containsAll(listOf("Starter", "Incorrect")))) {
            "File $path incorrectly contains multiple annotations: ${types.joinToString(separator = ", ") { "@$it" }}"
        }
    }.firstOrNull()

    val wrapWith = topLevelClass.getAnnotation(Wrap::class.java)?.let { className }
    val autoStarter = topLevelClass.getAnnotation(Wrap::class.java)?.let { annotation ->
        @Suppress("TooGenericExceptionCaught")
        try {
            annotation.parameterMap().let { parameters ->
                parameters["autoStarter"].toBoolean()
            }
        } catch (e: Exception) {
            error("Couldn't parse @Wrap metadata for $path: $e")
        }
    } ?: false

    val citation = topLevelClass.getAnnotation(Cite::class.java)?.let { annotation ->
        @Suppress("TooGenericExceptionCaught")
        try {
            annotation.parameterMap().let { parameters ->
                Question.Citation(parameters["source"]!!, parameters["link"])
            }
        } catch (e: Exception) {
            error("Couldn't parse @Wrap metadata for $path: $e")
        }
    }

    fun toCleanSolution(cleanSpec: CleanSpec): Pair<Question.FlatFile, Question.Type> = runBlocking {
        val solutionContent = clean(cleanSpec, false).let { content ->
            Source.fromJava(content).checkstyle(CheckstyleArguments(failOnError = false)).let { results ->
                val removeLines = results.errors.filter { error ->
                    error.message.trim().startsWith("Unused import")
                }.map { it.location.line }.toSet()
                content.lines().filterIndexed { index, _ -> !removeLines.contains(index + 1) }.joinToString("\n")
            }
        }.trimStart().let {
            Source.fromJava(it).googleFormat().contents
        }

        val cleanContentWithDead = solutionContent.javaDeTemplate(cleanSpec.hasTemplate, cleanSpec.wrappedClass)
        val deadlineCount = cleanContentWithDead.lines().filter {
            it.trim().endsWith("// dead code")
        }.size
        val cleanContent = cleanContentWithDead.lines().joinToString("\n") {
            it.trimEnd().removeSuffix("// dead code").trimEnd()
        }

        val questionType = cleanContent.getType()
        val source = when (questionType) {
            Question.Type.KLASS -> Source(mapOf("$className.java" to cleanContent))
            Question.Type.METHOD -> Source(
                mapOf(
                    "$className.java" to """public class $className {
$cleanContent
}""",
                ),
            )

            Question.Type.SNIPPET -> Source.fromSnippet(cleanContent)
        }
        val complexity = source.complexity().let { results ->
            when (questionType) {
                Question.Type.KLASS -> results.lookupFile("$className.java")
                Question.Type.METHOD -> results.lookup(className, "$className.java").complexity
                Question.Type.SNIPPET -> results.lookup("").complexity
            }
        }.also {
            check(it > 0) { "Invalid complexity value" }
        }
        val lineCounts = cleanContent.countLines(Source.FileType.JAVA)
        val features = source.features().let { features ->
            when (questionType) {
                Question.Type.KLASS -> features.lookup("", "$className.java")
                Question.Type.METHOD -> features.lookup(className, "$className.java")
                Question.Type.SNIPPET -> features.lookup("")
            }
        }.features

        return@runBlocking Pair(
            Question.FlatFile(
                className,
                cleanContent,
                Language.java,
                path,
                complexity,
                features,
                lineCounts,
                deadlineCount,
            ),
            questionType,
        )
    }

    fun extractTemplate(importNames: Set<String>): String? {
        val correctSolution = toCleanSolution(CleanSpec(false, null, importNames)).first.contents
        val templateStart = Regex("""//.*TEMPLATE_START""").find(correctSolution)?.range?.start ?: return null
        val templateEnd = correctSolution.indexOf("TEMPLATE_END")
        val start = correctSolution.substring(0 until templateStart)
        val end = correctSolution.substring((templateEnd + "TEMPLATE_END".length) until correctSolution.length)
        return "$start{{{ contents }}}$end"
    }

    fun extractStarter(): Question.IncorrectFile? {
        val correctSolution = toCleanSolution(CleanSpec(false, null)).first.contents
        val parsed = correctSolution.parseJava()
        val methodDeclaration = parsed.tree
            .typeDeclaration(0)
            ?.classDeclaration()
            ?.classBody()
            ?.classBodyDeclaration(0)
            ?.memberDeclaration()
            ?.methodDeclaration() ?: return null
        val start = methodDeclaration.methodBody().start.startIndex
        val end = methodDeclaration.methodBody().stop.stopIndex
        val starterReturn = when (methodDeclaration.typeTypeOrVoid().text) {
            "void" -> ""
            "String" -> " \"\""
            "byte" -> " 0"
            "short" -> " 0"
            "int" -> " 0"
            "long" -> " 0"
            "float" -> " 0.0"
            "double" -> " 0.0"
            "char" -> " ' '"
            "boolean" -> " false"
            else -> " null"
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
                "return$starterReturn; // You may need to remove this starter code" +
                correctSolution.substring(postfix until correctSolution.length)
            ).let {
            Formatter().formatSource(it)
        }.javaDeTemplate(false, wrapWith).let {
            Question.IncorrectFile(
                className,
                it,
                Question.IncorrectFile.Reason.TEST,
                Language.java,
                null,
                true,
            )
        }
    }

    fun toIncorrectFile(cleanSpec: CleanSpec): Question.IncorrectFile {
        check(incorrect != null) { "Not an incorrect file" }
        return Question.IncorrectFile(
            className,
            clean(cleanSpec).trimStart(),
            incorrect.toReason(),
            Language.java,
            path,
            starter != null,
        )
    }

    fun toAlternateFile(cleanSpec: CleanSpec): Question.FlatFile {
        check(alternateSolution != null) { "Not an alternate solution file" }
        val solutionContent = clean(cleanSpec, false).trimStart().let {
            Source.fromJava(it).googleFormat().contents
        }

        val cleanContentWithDead = solutionContent.javaDeTemplate(cleanSpec.hasTemplate, cleanSpec.wrappedClass)
        val deadlineCount = cleanContentWithDead.lines().filter {
            it.trim().endsWith("// dead code")
        }.size
        val cleanContent = cleanContentWithDead.lines().joinToString("\n") {
            it.trimEnd().removeSuffix("// dead code").trimEnd()
        }

        val questionType = cleanContent.getType()
        val source = when (questionType) {
            Question.Type.KLASS -> Source(mapOf("$className.java" to cleanContent))
            Question.Type.METHOD -> Source(
                mapOf(
                    "$className.java" to """public class $className {
                    |$cleanContent
                    }
                    """.trimMargin(),
                ),
            )

            Question.Type.SNIPPET -> Source.fromSnippet(cleanContent)
        }
        val complexity = source.complexity().let { results ->
            when (questionType) {
                Question.Type.KLASS -> results.lookupFile("$className.java")
                Question.Type.METHOD -> results.lookup(className, "$className.java").complexity
                Question.Type.SNIPPET -> results.lookup("").complexity
            }
        }.also {
            check(it > 0) { "Invalid complexity value" }
        }
        val lineCounts = cleanContent.countLines(Source.FileType.JAVA)
        val features = source.features().let { features ->
            when (questionType) {
                Question.Type.KLASS -> features.lookup("", "$className.java")
                Question.Type.METHOD -> features.lookup(className, "$className.java")
                Question.Type.SNIPPET -> features.lookup("")
            }
        }.features
        return Question.FlatFile(
            className,
            clean(cleanSpec).trimStart(),
            Language.java,
            path,
            complexity,
            features,
            lineCounts,
            deadlineCount,
        )
    }

    fun toStarterFile(cleanSpec: CleanSpec): Question.IncorrectFile {
        check(starter != null) { "Not an starter code file" }
        return Question.IncorrectFile(
            className,
            clean(cleanSpec).trimStart(),
            incorrect?.toReason() ?: "test".toReason(),
            Language.java,
            path,
            true,
        )
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

    fun removeImports(importNames: Set<String>): String {
        val toRemove = mutableSetOf<Int>()
        parseTree.importDeclaration().forEach { packageContext ->
            val packageName = packageContext.toFullName()
            if (packageName in importNames) {
                toRemove.add(packageContext.start.startIndex.toLine())
            }
        }
        return contents
            .split("\n")
            .filterIndexed { index, _ -> (index + 1) !in toRemove }
            .joinToString("\n")
            .trim()
    }

    @Suppress("ComplexMethod")
    fun clean(cleanSpec: CleanSpec, stripTemplate: Boolean = true): String = runBlocking {
        val (template, wrapWith, importNames) = cleanSpec

        val toSnip = mutableSetOf<IntRange>()
        val toRemove = mutableSetOf<Int>()
        parseTree.packageDeclaration()?.also {
            toRemove.add(it.start.startIndex.toLine())
        }
        parseTree.importDeclaration().forEach { packageContext ->
            val packageName = packageContext.toFullName()
            if (packageName in importsToRemove ||
                packageName in importNames ||
                packagesToRemove.any { packageName.startsWith(it) }
            ) {
                toRemove.add(packageContext.start.startIndex.toLine())
            }
        }
        topLevelClass.COMMENT()?.symbol?.also { token ->
            (token.startIndex.toLine()..token.stopIndex.toLine()).forEach { toRemove.add(it) }
        }
        topLevelClass.classOrInterfaceModifier().forEach { modifier ->
            modifier.annotation()?.also { annotation ->
                annotation.qualifiedName()?.asString()?.also { name ->
                    if (name in annotationsToRemove) {
                        (annotation.start.startIndex.toLine()..annotation.stop.stopIndex.toLine()).forEach {
                            toRemove.add(it)
                        }
                    }
                }
            }
        }
        topLevelClass.classDeclaration().classBody().classBodyDeclaration().forEach { classBodyDeclaration ->
            val annotations = classBodyDeclaration.modifier()
                .mapNotNull { it.classOrInterfaceModifier()?.annotation()?.qualifiedName()?.asString() }
            if (annotations.toSet().intersect(annotationsToDestroy).isNotEmpty()) {
                (classBodyDeclaration.start.startIndex.toLine()..classBodyDeclaration.stop.stopIndex.toLine()).forEach {
                    toRemove.add(it)
                }
            }
            classBodyDeclaration.modifier().mapNotNull { it.classOrInterfaceModifier()?.annotation() }
                .forEach { annotation ->
                    if (annotation.qualifiedName()?.asString()!! in annotationsToRemove) {
                        (annotation.start.startIndex.toLine()..annotation.stop.stopIndex.toLine()).forEach {
                            toRemove.add(it)
                        }
                    }
                }
            classBodyDeclaration.memberDeclaration()?.methodDeclaration()?.also { methodDeclaration ->
                methodDeclaration.formalParameters()?.formalParameterList()?.formalParameter()?.forEach { parameters ->
                    parameters.variableModifier().mapNotNull { it.annotation() }.forEach { annotation ->
                        if (annotation.qualifiedName()?.asString()!! in annotationsToSnip) {
                            toSnip.add(annotation.start.startIndex..annotation.stop.stopIndex)
                        }
                    }
                }
            }
            classBodyDeclaration.memberDeclaration()?.constructorDeclaration()?.also { constructorDeclaration ->
                constructorDeclaration.formalParameters()?.formalParameterList()?.formalParameter()
                    ?.forEach { parameters ->
                        parameters.variableModifier().mapNotNull { it.annotation() }.forEach { annotation ->
                            if (annotation.qualifiedName()?.asString()!! in annotationsToSnip) {
                                toSnip.add(annotation.start.startIndex..annotation.stop.stopIndex)
                            }
                        }
                    }
            }
        }

        return@runBlocking contents
            .let { unsnipped ->
                var snipped = unsnipped
                var shift = 0
                for (range in toSnip.sortedBy { it.first }) {
                    snipped = snipped.substring(0, range.first - shift) + snipped.substring(
                        range.last + 1 - shift,
                        snipped.length,
                    )
                    shift += (range.last - range.first) + 1
                }
                snipped
            }
            .split("\n")
            .filterIndexed { index, _ -> (index + 1) !in toRemove }
            .joinToString("\n")
            .trim()
            .let { Formatter().formatSource(it) }
            .let { content ->
                Source.fromJava(content).checkstyle(CheckstyleArguments(failOnError = false)).let { results ->
                    val unusedImports = results.errors.filter { error ->
                        error.message.trim().startsWith("Unused import")
                    }
                    val removeLines = unusedImports.map { it.location.line }.toSet()
                    require(correct != null || removeLines.isEmpty()) {
                        "Found unused imports in $path: ${unusedImports.joinToString(",") { it.message }}"
                    }
                    content.lines().filterIndexed { index, _ -> !removeLines.contains(index + 1) }
                        .joinToString("\n")
                }
            }.let {
                if (stripTemplate) {
                    it.javaDeTemplate(template, wrapWith)
                } else {
                    it
                }
            }
            .stripPackage()
    }

    var usedImports: List<String> = listOf()

    private fun String.javaDeTemplate(hasTemplate: Boolean, wrappedClass: String?): String {
        return when {
            wrappedClass != null -> {
                parseJava().tree.also { context ->
                    usedImports = context.importDeclaration().map { it.toFullName() }
                }.topLevelClass().let { context ->
                    val start = context.classDeclaration().start.line
                    val end = context.classDeclaration().stop.line
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
            }

            !hasTemplate -> this
            else -> {
                val lines = split("\n")
                val start = lines.indexOfFirst { it.contains("TEMPLATE_START") }
                val end = lines.indexOfFirst { it.contains("TEMPLATE_END") }
                require(start != -1) { "Couldn't locate TEMPLATE_START during extraction" }
                require(end != -1) { "Couldn't locate TEMPLATE_END during extraction" }
                lines.slice((start + 1) until end).joinToString("\n").trimIndent()
            }
        }
    }
}

data class ParsedJavaContent(
    val tree: JavaParser.CompilationUnitContext,
    val stream: CharStream,
)

internal fun String.parseJava() = CharStreams.fromStream(StringInputStream(this)).let { stream ->
    JavaLexer(stream).also { it.removeErrorListeners() }.let { lexer ->
        CommonTokenStream(lexer).let { tokens ->
            JavaParser(tokens).also { parser ->
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
        }.compilationUnit()
    }.let { tree ->
        ParsedJavaContent(tree, stream)
    }
}

fun JavaParser.CompilationUnitContext.topLevelClass(): JavaParser.TypeDeclarationContext =
    children.filterIsInstance<JavaParser.TypeDeclarationContext>().filter {
        it.classDeclaration() != null || it.interfaceDeclaration() != null
    }.let {
        check(it.isNotEmpty()) {
            "Couldn't find solution class. Make sure description comment is immediately above the @Correct annotation."
        }
        check(it.size == 1) {
            "Found multiple top-level classes"
        }
        it.first()
    }

fun JavaParser.TypeDeclarationContext.getAnnotation(annotation: Class<*>): JavaParser.AnnotationContext? =
    classOrInterfaceModifier().find {
        it.annotation()?.qualifiedName()?.asString() == annotation.simpleName
    }?.annotation()

fun JavaParser.TypeDeclarationContext.getAnnotations(annotation: Class<*>): List<JavaParser.AnnotationContext> =
    classOrInterfaceModifier().filter {
        it.annotation()?.qualifiedName()?.asString() == annotation.simpleName
    }.map { it.annotation() }

fun ElementValueContext.getValue() = expression().primary().literal().let { literal ->
    literal.STRING_LITERAL()
        ?: literal.integerLiteral()?.DECIMAL_LITERAL()
        ?: literal.BOOL_LITERAL()
        ?: literal.floatLiteral()?.FLOAT_LITERAL()
}.toString().removeSurrounding("\"")

fun JavaParser.AnnotationContext.parameterMap(): Map<String, String> =
    elementValuePairs()?.elementValuePair()?.associate {
        it.identifier().text to it.elementValue().getValue()
    } ?: elementValue()?.let {
        mapOf("value" to it.getValue())
    } ?: mapOf()

fun JavaParser.AnnotationContext.comment(): String {
    return when {
        parent.parent is JavaParser.TypeDeclarationContext ->
            (parent.parent as JavaParser.TypeDeclarationContext).COMMENT()

        parent.parent.parent is JavaParser.ClassBodyDeclarationContext ->
            (parent.parent.parent as JavaParser.ClassBodyDeclarationContext).COMMENT()

        else -> error("Error retrieving comment")
    }?.toString()?.split("\n")?.joinToString(separator = "\n") { line ->
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
    }?.trim() ?: error("Error retrieving comment")
}

fun JavaParser.QualifiedNameContext.asString() = identifier().joinToString(".") { it.text }

fun JavaParser.CompilationUnitContext.packageName() = packageDeclaration()?.qualifiedName()?.asString() ?: ""
fun JavaParser.CompilationUnitContext.className() = topLevelClass().let {
    if (it.classDeclaration() != null) {
        it.classDeclaration().identifier().text
    } else {
        it.interfaceDeclaration().identifier().text
    }
}.toString()

fun String.getType(): Question.Type {
    try {
        parseJava().tree.typeDeclaration(0).classDeclaration().classBody()
        return Question.Type.KLASS
    } catch (_: Exception) {
    }
    """public class Main {
            |$this
    """.trimMargin().parseJava().also { parsed ->
        parsed.tree
            .typeDeclaration(0)
            ?.classDeclaration()
            ?.classBody()
            ?.classBodyDeclaration(0)
            ?.memberDeclaration()
            ?.methodDeclaration() ?: return Question.Type.SNIPPET
        return Question.Type.METHOD
    }
}

fun String.methodIsMarkedPublicOrStatic(): Boolean {
    """public class Main {
            |$this
    """.trimMargin().parseJava().also { parsed ->
        return parsed.tree
            .typeDeclaration(0)
            .classDeclaration()
            .classBody().classBodyDeclaration().any { declaration ->
                declaration.memberDeclaration()?.methodDeclaration() != null && declaration.modifier().any {
                    it.classOrInterfaceModifier()?.PUBLIC() != null
                }
            } || parsed.tree
            .typeDeclaration(0)
            .classDeclaration()
            .classBody().classBodyDeclaration().any { declaration ->
                declaration.memberDeclaration()?.methodDeclaration() != null && declaration.modifier().all {
                    it.classOrInterfaceModifier()?.PRIVATE() == null
                } && declaration.modifier().any {
                    it.classOrInterfaceModifier()?.STATIC() != null
                }
            }
    }
}
