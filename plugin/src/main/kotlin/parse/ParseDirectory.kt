package edu.illinois.cs.cs125.questioner.plugin.parse

import com.github.slugify.Slugify
import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.lib.Question
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.loadQuestion
import edu.illinois.cs.cs125.questioner.lib.makeLanguageMap
import org.jetbrains.annotations.NotNull
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.stream.Collectors
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

private val slugify = Slugify.builder().build()

fun Path.parseDirectory(
    baseDirectory: Path,
    inputPackageMap: Map<String, List<String>>?,
    force: Boolean = false,
    questionerVersion: String = VERSION,
    rootDirectory: Path = Path.of("/"),
): Question {
    val packageMap = inputPackageMap ?: baseDirectory.buildPackageMap()

    val outputFile = parent.resolve(".question.json")
    val existingQuestion = outputFile.toFile().loadQuestion()

    fun Set<String>.relativize() = map { Path.of(it).relativeTo(rootDirectory).toString() }

    val allFiles = allFiles()
    if (!force &&
        existingQuestion != null &&
        existingQuestion.published.questionerVersion == questionerVersion &&
        existingQuestion.metadata?.allFiles == allFiles.map { file -> file.path }.toSet().relativize() &&
        outputFile.toFile().lastModified() > newestFile().lastModified()
    ) {
        return existingQuestion
    }

    val contentHash = directoryHash(questionerVersion)
    if (!force && existingQuestion?.published?.contentHash == contentHash) {
        return existingQuestion
    }

    val solution = ParsedJavaFile(toFile())
    check(solution.correct != null) { "Solutions should have @Correct metadata" }
    check(solution.packageName != "") { "Solutions should not have an empty package name" }
    check(solution.className != "") { "Solutions should not have an empty class name" }

    val parsedJavaFiles = allFiles.filter { it.name.endsWith(".java") }.map { ParsedJavaFile(it) }
    val parsedKotlinFiles = allFiles.filter { it.path.endsWith(".kt") }.map { ParsedKotlinFile(it) }

    parsedJavaFiles.filter { it.isCorrect && it.path != solution.path }.let { otherSolutions ->
        check(otherSolutions.isEmpty()) {
            """Solutions cannot be nested: file://${otherSolutions.first().path} is inside file://${Path.of(solution.path).parent}"""
        }
    }

    val usedFiles = parsedJavaFiles.filter { it.isCorrect }.associate { it.path to "Correct" }.toMutableMap()
    fun addUsedFile(path: String, whatFor: String, canBe: Set<String> = setOf()) {
        check(path !in usedFiles || canBe.contains(usedFiles[path])) {
            """file://$path already used as file://${usedFiles[path]!!}"""
        }
        usedFiles[path] = whatFor
    }

    val commonContent = parsedJavaFiles.filter { parsedJavaFile ->
        !parsedJavaFile.isCorrect && Path.of(parsedJavaFile.path).parent == parent
    }.also { files ->
        files.find { it.isQuestioner }?.also {
            error("""@Incorrect, @Starter, and alternate solutions should not be in the same directory as the reference solution: file://${it.path})""")
        }
        files.forEach { file ->
            addUsedFile(file.path, "Common")
        }
    }
    val commonImports =
        commonContent.map { parsedJavaFile -> "${parsedJavaFile.packageName}.${parsedJavaFile.className}" }.toSet()

    val localImports = solution.importsToPackages().map {
        packageMap[it] ?: listOf()
    }.flatten().mapNotNull { Path.of(it) }.asSequence()
    val importNames = localImports.getLocalImportNames(baseDirectory) + commonImports

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

    val parsedKotlinSolution = parsedKotlinFiles.find { it.isAlternateSolution && it.description != null }
    if (parsedKotlinFiles.any { it.isAlternateSolution }) {
        check(parsedKotlinSolution != null) {
            """Found Kotlin solutions but no description comment: file://${solution.path}"""
        }
    }

    parsedKotlinFiles.filter { it.isAlternateSolution }.forEach {
        check(!it.hasControlAnnotations) {
            """Found control annotations on an @AlternateSolution. Please remove them: file://${it.path}"""
        }
    }

    if (solution.autoStarter) {
        parsedKotlinSolution?.extractStarter(solution.wrapWith)
    }

    val hasJavaTemplate = solution.contents.lines().let { lines ->
        lines.any { it.contains("TEMPLATE_START") } && lines.any { it.contains("TEMPLATE_END") }
    }
    val hasKotlinTemplate = parsedKotlinSolution?.contents?.lines()?.let { lines ->
        lines.any { it.contains("TEMPLATE_START") } && lines.any { it.contains("TEMPLATE_END") }
    } ?: false

    val javaCleanSpec = CleanSpec(hasJavaTemplate, solution.wrapWith, importNames)
    val kotlinCleanSpec = CleanSpec(hasKotlinTemplate, solution.wrapWith, importNames)

    if (hasJavaTemplate && javaTemplate == null && solution.wrapWith == null) {
        javaTemplate = solution.extractTemplate(importNames) ?: error(
            """Can't extract Java template: file://${solution.path}""",
        )
    }

    if (hasKotlinTemplate && kotlinTemplate == null && solution.wrapWith == null) {
        kotlinTemplate = parsedKotlinSolution!!.extractTemplate(importNames) ?: error(
            """Can't extract Kotlin template: file://${parsedKotlinSolution.path}""",
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

    val alternativeSolutions = parsedJavaFiles.filter { it.isAlternateSolution }
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

    val commonFiles = (
        localImports.getLocalImportPaths().map { path ->
            addUsedFile(path.toString(), "Common")
            ParsedJavaFile(path.toFile()).forCommon()
        } + commonContent.map { file -> file.forCommon() }
        ).also { commonFileList ->
        val classNames = commonFileList.map { it.klass }
        val duplicateClasses = classNames.groupingBy { it }.eachCount().filter { (_, v) -> v >= 2 }.keys
        check(duplicateClasses.isEmpty()) {
            "Found duplicate classes in common code: ${duplicateClasses.joinToString(",")}"
        }
        check(!classNames.contains(solution.className)) {
            "Common code contains class with same name as solution: ${solution.className}"
        }
    }

    val javaStarter = parsedJavaFiles
        .filter { it.isStarter }
        .also {
            check(it.size <= 1) {
                """Solution ${solution.correct.name} provided multiple files marked as starter code"""
            }
        }.firstOrNull()

    check(!(solution.autoStarter && javaStarter != null)) {
        """autoStarter set to true but found a file marked as @Starter. Please remove it: file://${javaStarter!!.path}"""
    }

    val javaStarterFile = if (solution.autoStarter) {
        solution.extractStarter()
            ?: error("""autoStarter enabled but starter generation failed""")
    } else {
        javaStarter?.toStarterFile(javaCleanSpec)?.also {
            addUsedFile(it.path!!, "Starter", setOf("Incorrect"))
        }
    }

    var kotlinStarterFile = parsedKotlinFiles.filter { it.isStarter }.also {
        check(it.size <= 1) { """Provided multiple file with Kotlin starter code""" }
    }.firstOrNull()?.let {
        addUsedFile(it.path, "Starter", setOf("Incorrect"))
        it.toStarterFile(kotlinCleanSpec)
    }

    if (solution.autoStarter) {
        val autoStarter = parsedKotlinSolution?.extractStarter(solution.wrapWith)
        if (autoStarter != null && kotlinStarterFile != null) {
            error("""autoStarter succeeded but Kotlin starter file found. Please remove it: file://${kotlinStarterFile.path}""")
        }
        kotlinStarterFile = autoStarter
    }

    // Needed to set imports properly
    solution.clean(javaCleanSpec)

    val templateImports = (solution.usedImports + solution.templateImports).toMutableSet()
    // HACK HACK: Allow java.util.Set methods when java.util.Map is used and no @TemplateImports
    if (!solution.hasTemplateImports && templateImports.contains("java.util.Map")) {
        templateImports += "java.util.Set"
    }
    val kotlinImports = ((parsedKotlinSolution?.usedImports ?: listOf()) + solution.templateImports).toSet()

    templateImports
        .filter { it.endsWith(".NotNull") || it.endsWith(".NonNull") }
        .filter { it != NotNull::class.java.name }
        .also {
            check(it.isEmpty()) {
                """Please use the Questioner @NotNull annotation from ${NotNull::class.java.name}"""
            }
        }

    kotlinImports
        .filter { it.endsWith(".NotNull") || it.endsWith(".NonNull") }
        .also {
            check(it.isEmpty()) {
                """@NotNull or @NonNull annotations will not be applied when used in Kotlin solutions"""
            }
        }

    if (solution.wrapWith != null) {
        check(javaTemplate == null && kotlinTemplate == null) {
            """Can't use both a template and @Wrap"""
        }

        javaTemplate = """public class ${solution.wrapWith} {
                |  {{{ contents }}}
                |}
        """.trimMargin()
        if (templateImports.isNotEmpty()) {
            javaTemplate = templateImports.joinToString("\n") { "import $it;" } + "\n\n$javaTemplate"
        }

        if (parsedKotlinSolution != null) {
            kotlinTemplate = if (parsedKotlinSolution.topLevelFile) {
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

    val (javaSolution, type) = solution.toCleanSolution(javaCleanSpec)

    if (type == Question.Type.METHOD) {
        check(!javaSolution.contents.methodIsMarkedPublicOrStatic()) {
            """Do not use public modifiers on method-only problems, and use static only on private helpers"""
        }
    }

    val javaDescription = solution.correct.description
    val kotlinDescription = parsedKotlinSolution?.description

    val metadata = Question.Metadata(
        allFiles.map { file -> file.path }.toSet().relativize().toSet(),
        allFiles
            .map { file -> file.path }
            .filter { path -> !usedFiles.containsKey(path) }
            .toSet().relativize().toSet(),
        solution.correct.focused,
        solution.correct.publish,
    )

    if (kotlinDescription != null) {
        val hasJavaStarter = incorrectExamples.any { it.language == Language.java && it.starter }
        val hasKotlinStarter = incorrectExamples.any { it.language == Language.kotlin && it.starter }
        if (hasJavaStarter) {
            check(hasKotlinStarter) { """Kotlin starter code is missing""" }
        }
    }

    val slug = solution.correct.path ?: slugify.slugify(solution.correct.name)
    val detemplatedJavaStarter = incorrectExamples.find { it.language == Language.java && it.starter }?.contents
    val detemplatedKotlinStarter = incorrectExamples.find { it.language == Language.kotlin && it.starter }?.contents

    val hasKotlin: Boolean = kotlinDescription != null

    val kotlinSolution = parsedKotlinSolution?.toAlternateFile(kotlinCleanSpec)

    val kotlinComplexity = alternativeSolutions
        .filter { it.language == Language.kotlin }
        .mapNotNull { it.complexity }
        .minOrNull()

    val published = Question.Published(
        contentHash = contentHash,
        klass = solution.className,
        path = slug,
        author = solution.correct.author,
        authorName = solution.correct.authorName,
        version = solution.correct.version,
        name = solution.correct.name,
        type = type,
        citation = solution.citation,
        packageName = solution.packageName,
        languages = mutableSetOf(Language.java).apply {
            if (hasKotlin) {
                add(Language.kotlin)
            }
        }.toSet(),
        descriptions = makeLanguageMap(javaDescription, kotlinDescription)!!,
        starters = makeLanguageMap(detemplatedJavaStarter, detemplatedKotlinStarter),
        templateImports = templateImports,
        questionerVersion = questionerVersion,
        tags = solution.tags.toMutableSet(),
        kotlinImports = kotlinImports,
        javaTestingImports = templateImports + setOf("org.junit.Assert"),
        kotlinTestingImports = kotlinImports + setOf("org.junit.Assert"),
    )

    val classification = Question.Classification(
        featuresByLanguage = makeLanguageMap(javaSolution.features!!, kotlinSolution?.features)!!,
        lineCounts = makeLanguageMap(javaSolution.lineCount!!, kotlinSolution?.lineCount)!!,
        complexity = makeLanguageMap(javaSolution.complexity!!, kotlinComplexity)!!,
    )

    val question = Question.FlatFile(
        klass = solution.className,
        contents = solution.removeImports(importNames).stripPackage(),
        language = Language.java,
        path = Path.of(solution.path).relativeTo(rootDirectory).toString(),
        suppressions = solution.suppressions,
    )

    return Question(
        published = published,
        classification = classification,
        metadata = metadata,
        annotatedControls = solution.correct.control,
        question = question,
        solutionByLanguage = makeLanguageMap(
            javaSolution.copy(
                path = javaSolution.path?.let { Path.of(it).relativeTo(rootDirectory).toString() },
            ),
            kotlinSolution?.copy(
                path = kotlinSolution.path?.let { Path.of(it).relativeTo(rootDirectory).toString() },
            ),
        )!!,
        alternativeSolutions = alternativeSolutions.map { correct ->
            correct.copy(path = correct.path?.let { path -> Path.of(path).relativeTo(rootDirectory).toString() })
        },
        incorrectExamples = incorrectExamples.map { incorrect ->
            incorrect.copy(path = incorrect.path?.let { path -> Path.of(path).relativeTo(rootDirectory).toString() })
        },
        common = null,
        commonFiles = commonFiles,
        templateByLanguage = makeLanguageMap(javaTemplate, kotlinTemplate),
        importWhitelist = solution.whitelist,
        importBlacklist = solution.blacklist,
    ).also { loadedQuestion ->
        check(loadedQuestion.control.minTestCount!! <= loadedQuestion.control.maxTestCount!!) {
            "Question minTestCount (${loadedQuestion.control.minTestCount}) > maxTestCount (${loadedQuestion.control.maxTestCount})"
        }
        if (loadedQuestion.control.maxMutationCount != null) {
            check(loadedQuestion.control.minMutationCount!! <= loadedQuestion.control.maxMutationCount!!) {
                "Question minMutationCount (${loadedQuestion.control.minMutationCount}) > maxMutationCount (${loadedQuestion.control.maxMutationCount})"
            }
        }
    }
}

fun Path.allFiles() = Files.walk(parent)
    .filter { path ->
        Files.isRegularFile(path) &&
            !Files.isDirectory(path) &&
            (path.name.endsWith(".java") || path.name.endsWith(".kt"))
    }
    .map { path -> path.toFile() }
    .collect(Collectors.toList())
    .toList()
    .sortedBy { it.path }

@Suppress("unused")
private fun Path.newestFile(): File = allFiles().sortedBy { it.lastModified() }.reversed().first()
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

private fun String.pathToImport() = removeSuffix(".java").replace(FileSystems.getDefault().separator, ".")

private fun Path.toJavaFile() = resolveSibling("${fileName.toString().removeSuffix(".java")}.java")

private fun ParsedJavaFile.importsToPackages() = listedImports.map { importName ->
    importName.split(".").dropLast(1).joinToString(".")
}.toSet()

private fun Sequence<Path>.getLocalImportPaths() = map { path ->
    when {
        path.toJavaFile().isRegularFile() -> listOf(path.toJavaFile())
        else -> error("""Invalid path type: file://$path""")
    }
}.flatten().toSet()

private fun Sequence<Path>.getLocalImportNames(baseDirectory: Path) = map { path ->
    when {
        path.toJavaFile().isRegularFile() -> path.relativeTo(baseDirectory).toString().pathToImport()
        else -> error("""Invalid path type: file://$path""")
    }
}.toSet()
