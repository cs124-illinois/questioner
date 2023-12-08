package edu.illinois.cs.cs125.questioner.plugin.parse

import com.github.slugify.Slugify
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.loadQuestion
import org.jetbrains.annotations.NotNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.stream.Collectors
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

private val slugify = Slugify.builder().build()

fun Path.parseDirectory(baseDirectory: Path, force: Boolean = false, questionerVersion: String = VERSION): Question {
    val outputFile = parent.resolve(".question.json")
    val existingQuestion = outputFile.toFile().loadQuestion()

    if (!force && existingQuestion != null && outputFile.toFile().lastModified() > newestFile().lastModified()) {
        return existingQuestion
    }

    val contentHash = directoryHash(questionerVersion)
    if (!force && existingQuestion?.metadata?.contentHash == contentHash) {
        return existingQuestion
    }

    val solution = ParsedJavaFile(toFile())
    require(solution.correct != null) { "Solutions should have @Correct metadata" }
    require(solution.packageName != "") { "Solutions should not have an empty package name" }
    require(solution.className != "") { "Solutions should not have an empty class name" }

    val allFiles = parent.allFiles()
    val parsedJavaFiles = allFiles.filter { it.name.endsWith(".java") }.map { ParsedJavaFile(it) }
    val parsedKotlinFiles = allFiles.filter { it.path.endsWith(".kt") }.map { ParsedKotlinFile(it) }

    parsedJavaFiles.filter { it.isCorrect && it.path != solution.path }.let { otherSolutions ->
        require(otherSolutions.isEmpty()) {
            "Solutions cannot be nested: ${otherSolutions.first().path} is inside ${Path.of(solution.path).parent}"
        }
    }

    val usedFiles = parsedJavaFiles.filter { it.isCorrect }.associate { it.path to "Correct" }.toMutableMap()
    fun addUsedFile(path: String, whatFor: String, canBe: Set<String> = setOf()) {
        require(path !in usedFiles || canBe.contains(usedFiles[path])) {
            "$path already used as ${usedFiles[path]!!}"
        }
        usedFiles[path] = whatFor
    }

    val commonFiles = parsedJavaFiles.filter { parsedJavaFile ->
        !parsedJavaFile.isCorrect && Path.of(parsedJavaFile.path).parent == parent
    }.also { files ->
        files.find { it.isQuestioner }?.also {
            error("@Incorrect, @Starter, and alternate solutions should not be in the same directory as the reference solution: ${it.path})")
        }
        files.forEach { file ->
            addUsedFile(file.path, "Common")
        }
    }
    val commonImports =
        commonFiles.map { parsedJavaFile -> "${parsedJavaFile.packageName}.${parsedJavaFile.className}" }.toSet()

    val mappedImports = solution.getMappedImports(baseDirectory)
    val importNames = mappedImports.getLocalImportNames(baseDirectory) + commonImports

    var javaTemplate = File("${solution.path}.hbs").let { file ->
        when {
            file.exists() -> file
            else -> null
        }
    }?.also {
        addUsedFile(it.path, "Template")
    }?.readText()?.stripPackage()

    var kotlinTemplate = File("${solution.path.replace(".java$".toRegex(), ".kt")}.hbs").let {
        if (it.path != "${solution.path}.hbs" && it.exists()) {
            it
        } else {
            null
        }
    }?.also {
        addUsedFile(it.path, "Template")
    }?.readText()?.stripPackage()

    val kotlinSolution = parsedKotlinFiles.find { it.isAlternateSolution && it.description != null }
    if (parsedKotlinFiles.any { it.isAlternateSolution }) {
        check(kotlinSolution != null) {
            "Found Kotlin solutions but no description comment"
        }
    }

    if (solution.autoStarter) {
        kotlinSolution?.extractStarter(solution.wrapWith)
    }

    val hasJavaTemplate = solution.contents.lines().let { lines ->
        lines.any { it.contains("TEMPLATE_START") } && lines.any { it.contains("TEMPLATE_END") }
    }
    val hasKotlinTemplate = kotlinSolution?.contents?.lines()?.let { lines ->
        lines.any { it.contains("TEMPLATE_START") } && lines.any { it.contains("TEMPLATE_END") }
    } ?: false

    val javaCleanSpec = CleanSpec(hasJavaTemplate, solution.wrapWith, importNames)
    val kotlinCleanSpec = CleanSpec(hasKotlinTemplate, solution.wrapWith, importNames)

    if (hasJavaTemplate && javaTemplate == null && solution.wrapWith == null) {
        javaTemplate = solution.extractTemplate(importNames) ?: error(
            "Can't extract Java template",
        )
    }

    if (hasKotlinTemplate && kotlinTemplate == null && solution.wrapWith == null) {
        kotlinTemplate = kotlinSolution!!.extractTemplate(importNames) ?: error(
            "Can't extract Kotlin template",
        )
    }

    val incorrectExamples = parsedJavaFiles.filter { it.isIncorrect }
        .onEach { addUsedFile(it.path, "Incorrect") }
        .map { it.toIncorrectFile(javaCleanSpec) }.toMutableList().apply {
            addAll(
                parsedKotlinFiles.filter { it.isIncorrect }
                    .onEach { addUsedFile(it.path, "Incorrect") }
                    .map { it.toIncorrectFile(kotlinCleanSpec) },
            )
        }

    val alternateSolutions = parsedJavaFiles.filter { it.isAlternateSolution }
        .onEach { addUsedFile(it.path, "Alternate") }
        .map {
            it.toAlternateFile(javaCleanSpec)
        }.toMutableList().apply {
            addAll(
                parsedKotlinFiles
                    .filter { it.isAlternateSolution }
                    .onEach { addUsedFile(it.path, "Correct") }
                    .map { it.toAlternateFile(kotlinCleanSpec) },
            )
        }.toList()

    val common = mappedImports.getLocalImportPaths().map { path ->
        addUsedFile(path.toString(), "Common")
        ParsedJavaFile(path.toFile()).contents.stripPackage()
    } + commonFiles.map { file ->
        file.contents.stripPackage()
    }

    val javaStarter = parsedJavaFiles
        .filter { it.isStarter }
        .also {
            assert(it.size <= 1) {
                "Solution ${solution.correct.name} provided multiple files marked as starter code"
            }
        }.firstOrNull()

    check(!(solution.autoStarter && javaStarter != null)) {
        "autoStarter set to true but found a file marked as @Starter. Please remove it.\n${javaStarter!!.path}"
    }

    val javaStarterFile = if (solution.autoStarter) {
        solution.extractStarter() ?: error("autoStarter enabled but starter generation failed")
    } else {
        javaStarter?.toStarterFile(javaCleanSpec)?.also {
            addUsedFile(it.path!!, "Starter", setOf("Incorrect"))
        }
    }

    var kotlinStarterFile = parsedKotlinFiles.filter { it.isStarter }.also {
        require(it.size <= 1) { "Provided multiple file with Kotlin starter code" }
    }.firstOrNull()?.let {
        addUsedFile(it.path, "Starter", setOf("Incorrect"))
        it.toStarterFile(kotlinCleanSpec)
    }

    if (solution.autoStarter) {
        val autoStarter = kotlinSolution?.extractStarter(solution.wrapWith)
        if (autoStarter != null && kotlinStarterFile != null) {
            error("autoStarter succeeded but Kotlin starter file found. Please remove it.\n" + kotlinStarterFile.path)
        }
        kotlinStarterFile = autoStarter
    }

    // Needed to set imports properly
    solution.clean(javaCleanSpec)

    val javaImports = (solution.usedImports + solution.templateImports).toMutableSet()
    // HACK HACK: Allow java.util.Set methods when java.util.Map is used and no @TemplateImports
    if (!solution.hasTemplateImports && javaImports.contains("java.util.Map")) {
        javaImports += "java.util.Set"
    }
    val kotlinImports = ((kotlinSolution?.usedImports ?: listOf()) + solution.templateImports).toSet()

    javaImports
        .filter { it.endsWith(".NotNull") || it.endsWith(".NonNull") }
        .filter { it != NotNull::class.java.name }
        .also {
            require(it.isEmpty()) {
                "Please use the Questioner @NotNull annotation from ${NotNull::class.java.name}"
            }
        }

    kotlinImports
        .filter { it.endsWith(".NotNull") || it.endsWith(".NonNull") }
        .also {
            require(it.isEmpty()) {
                "@NotNull or @NonNull annotations will not be applied when used in Kotlin solutions"
            }
        }

    if (solution.wrapWith != null) {
        require(javaTemplate == null && kotlinTemplate == null) {
            "Can't use both a template and @Wrap"
        }

        javaTemplate = """public class ${solution.wrapWith} {
                |  {{{ contents }}}
                |}
        """.trimMargin()
        if (javaImports.isNotEmpty()) {
            javaTemplate = javaImports.joinToString("\n") { "import $it;" } + "\n\n$javaTemplate"
        }

        if (kotlinSolution != null) {
            kotlinTemplate = if (kotlinSolution.topLevelFile) {
                "{{{ contents }}}"
            } else {
                """class ${solution.wrapWith} {
                |  {{{ contents }}}
                |}
                """.trimMargin()
            }

            if (kotlinImports.isNotEmpty()) {
                kotlinTemplate = kotlinImports.joinToString("\n") { "import $it" } + "\n\n$kotlinTemplate"
            }
        }
    }

    kotlinStarterFile?.also { incorrectExamples.add(0, it) }
    javaStarterFile?.also { incorrectExamples.add(0, it) }

    val (cleanSolution, questionType) = solution.toCleanSolution(javaCleanSpec)

    if (questionType == Question.Type.METHOD) {
        check(!cleanSolution.contents.methodIsMarkedPublicOrStatic()) {
            "Do not use public modifiers on method-only problems, and use static only on private helpers: ${solution.path}"
        }
    }

    val metadata = Question.Metadata(
        contentHash,
        solution.packageName,
        solution.correct.version,
        solution.correct.author,
        solution.correct.description,
        kotlinSolution?.description,
        solution.citation,
        usedFiles.keys.map { path -> baseDirectory.relativize(Path.of(path)).toString() }.toSet(),
        allFiles
            .map { file -> file.path }
            .filter { path -> !usedFiles.containsKey(path) }
            .map { path ->
                baseDirectory.relativize(Path.of(path)).toString()
            }.toSet(),
        javaImports,
        solution.correct.focused,
        solution.correct.publish,
        questionerVersion,
    )

    if (metadata.kotlinDescription != null && kotlinSolution != null) {
        val hasJavaStarter = incorrectExamples.any { it.language == Language.java && it.starter }
        val hasKotlinStarter = incorrectExamples.any { it.language == Language.kotlin && it.starter }
        if (hasJavaStarter) {
            check(hasKotlinStarter) { "Kotlin starter code is missing for ${solution.path}" }
        }
    }

    return Question(
        solution.correct.name,
        questionType,
        solution.className,
        metadata,
        solution.correct.control,
        Question.FlatFile(
            solution.className,
            solution.removeImports(importNames).stripPackage(),
            Language.java,
            solution.path,
            suppressions = solution.suppressions,
        ),
        cleanSolution,
        alternateSolutions,
        incorrectExamples,
        common,
        javaTemplate,
        kotlinTemplate,
        solution.whitelist,
        solution.blacklist,
        solution.checkstyleSuppress,
        solution.correct.path ?: slugify.slugify(solution.correct.name),
        kotlinSolution?.toAlternateFile(kotlinCleanSpec),
    )
}

fun Path.allFiles() = Files.walk(this)
    .filter { path ->
        Files.isRegularFile(path) && !Files.isDirectory(path) && (
            path.name.endsWith(".java") || path.name.endsWith(
                ".kt",
            )
            )
    }
    .map { path -> path.toFile() }
    .collect(Collectors.toList())
    .toList()
    .sortedBy { it.path }

@Suppress("unused")
fun Path.newestFile(): File = allFiles().sortedBy { it.lastModified() }.reversed().first()
private fun Path.directoryHash(questionerVersion: String) = MessageDigest.getInstance("MD5").let { md5 ->
    allFiles().forEach { file ->
        DigestInputStream(file.inputStream(), md5).apply {
            while (available() > 0) {
                read()
            }
            close()
        }
    }
    "${md5.digest().fold("") { str, it -> str + "%02x".format(it) }}-v$questionerVersion"
}

private fun String.importToPath() = replace(".", System.getProperty("file.separator"))
private fun String.pathToImport() = replace(System.getProperty("file.separator"), ".")

private fun Path.toJavaFile() = resolveSibling("${fileName.toString().removeSuffix(".java")}.java")
private fun ParsedJavaFile.getMappedImports(baseDirectory: Path) = listedImports
    .asSequence()
    .map { importName ->
        when {
            importName.endsWith(".*") -> Pair(importName.removeSuffix(".*"), true)
            else -> Pair(importName, false)
        }
    }
    .map { (importName, isDirectory) -> Pair(baseDirectory.resolve(importName.importToPath()), isDirectory) }
    .filter { (importPath, isDirectory) ->
        when (isDirectory) {
            true -> importPath.isDirectory()
            false -> importPath.resolveSibling("${importPath.fileName}.java").isRegularFile()
        }
    }.map { (importPath) -> importPath }
    .filterNotNull()

private fun Sequence<Path>.getLocalImportPaths() = map { path ->
    when {
        path.isDirectory() ->
            Files.walk(path, 1).filter { it.endsWith(".java") || it.endsWith(".kt") }.collect(Collectors.toList())

        path.toJavaFile().isRegularFile() -> listOf(path.toJavaFile())
        else -> error("Invalid file type")
    }
}.flatten().toSet()

private fun Sequence<Path>.getLocalImportNames(baseDirectory: Path) = map { path ->
    when {
        path.isDirectory() -> path.relativeTo(baseDirectory).toString().pathToImport() + ".*"

        path.toJavaFile().isRegularFile() -> path.relativeTo(baseDirectory).toString().pathToImport()

        else -> error("Invalid file type: $path")
    }
}.toSet()
