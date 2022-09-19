@file:Suppress("TooManyFunctions")

package edu.illinois.cs.cs125.questioner.plugin.save

import com.github.slugify.Slugify
import edu.illinois.cs.cs125.jenisol.core.Both
import edu.illinois.cs.cs125.jenisol.core.Compare
import edu.illinois.cs.cs125.jenisol.core.Configure
import edu.illinois.cs.cs125.jenisol.core.DesignOnly
import edu.illinois.cs.cs125.jenisol.core.EdgeType
import edu.illinois.cs.cs125.jenisol.core.FilterParameters
import edu.illinois.cs.cs125.jenisol.core.FixedParameters
import edu.illinois.cs.cs125.jenisol.core.InstanceValidator
import edu.illinois.cs.cs125.jenisol.core.Limit
import edu.illinois.cs.cs125.jenisol.core.NotNull
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
import edu.illinois.cs.cs125.questioner.lib.TemplateImports
import edu.illinois.cs.cs125.questioner.lib.Whitelist
import edu.illinois.cs.cs125.questioner.lib.Wrap
import edu.illinois.cs.cs125.questioner.lib.loadQuestions
import edu.illinois.cs.cs125.questioner.lib.saveQuestions
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.stream.Collectors

private val slugify = Slugify.builder().build()

@Suppress("unused")
abstract class SaveQuestions : DefaultTask() {
    init {
        group = "Build"
        description = "Save questions to JSON."
    }

    @InputFiles
    val inputFiles: FileCollection = project.extensions.getByType(JavaPluginExtension::class.java)
        .sourceSets.getByName("main").allSource.filter { it.name.endsWith(".java") || it.name.endsWith(".kt") }

    @OutputFile
    val outputFile = File(project.buildDir, "questioner/questions.json")

    @TaskAction
    fun save() {
        val existingQuestions = outputFile.loadQuestions().values.associateBy { it.metadata.contentHash }
        inputFiles.filter { it.name.endsWith(".java") }
            .map { ParsedJavaFile(it) }
            .findQuestions(inputFiles.map { it.path }, existingQuestions)
            .associateBy { it.name }
            .also {
                outputFile.saveQuestions(it)
            }
    }
}

@Suppress("LongMethod", "ComplexMethod")
fun List<ParsedJavaFile>.findQuestions(
    allPaths: List<String>,
    existingQuestions: Map<String, Question> = mapOf()
): List<Question> {
    map { it.fullName }.groupingBy { it }.eachCount().filter { it.value > 1 }.also { duplicates ->
        if (duplicates.isNotEmpty()) {
            error("Files with duplicate qualified names found: ${duplicates.keys}")
        }
    }

    val byFullName = associate { it.fullName to it }

    val solutions = filter { it.correct != null }
    solutions.also {
        val namesByPath = mutableMapOf<String, String>()
        val citationsByPath = mutableMapOf<String, String>()
        solutions.forEach {
            val name = it.correct!!.name
            if (name in namesByPath) {
                error("Found duplicate name: $name used by both ${it.path} and ${namesByPath[it.correct.name]}")
            }
            namesByPath[it.correct.name] = it.path
            if (it.citation?.link != null) {
                if (it.citation.link!! in citationsByPath) {
                    error("Found duplicate citation: ${it.citation.link} used by both ${it.path} and ${citationsByPath[it.citation.link!!]}")
                }
                citationsByPath[it.citation.link!!] = it.path
            }
        }
    }
    solutions.map { it.packageName }.sorted().zipWithNext().forEach { (first, second) ->
        if (second.startsWith("$first.")) {
            error("Question package names cannot be nested: $second is inside $first")
        }
    }

    val otherFiles = filter { it.correct == null }
    val usedFiles = solutions.associate { it.path to "Correct" }.toMutableMap()
    val skippedFiles = mutableListOf<String>()
    val knownFiles = map { it.path }

    val javaDescriptionsByPath = mutableMapOf<String, String>()
    val kotlinDescriptionsByPath = mutableMapOf<String, String>()

    val questions = solutions.map { solution ->
        require(solution.correct != null) { "Solutions should have @Correct metadata" }
        require(solution.packageName != "") { "Solutions should not have an empty package name" }
        require(solution.className != "") { "Solutions should not have an empty class name" }

        val myUsedFiles = mutableListOf<String>()
        try {
            val associatedFiles = if (Files.isRegularFile(Paths.get(solution.path))) {
                Files.walk(Paths.get(solution.path).parent)
                    .filter { Files.isRegularFile(it) && !Files.isDirectory(it) }
                    .map { it.toString() }
                    .filter { it.endsWith(".java") || it.endsWith(".kt") }
                    .map { File(it) }
                    .collect(Collectors.toList())
                    .toList()
                    .sortedBy { it.path }
            } else {
                listOf()
            }
            val allContentHash = if (Files.isRegularFile(Paths.get(solution.path))) {
                val md = MessageDigest.getInstance("MD5")
                associatedFiles.forEach {
                    DigestInputStream(it.inputStream(), md).apply {
                        while (available() > 0) {
                            read()
                        }
                        close()
                    }
                }
                md.digest().fold("") { str, it -> str + "%02x".format(it) }
            } else {
                ""
            }

            existingQuestions[allContentHash]?.also { question ->
                skippedFiles.addAll(question.metadata.usedFiles)
                return@map question
            }

            val neighborImports = Files.walk(Paths.get(solution.path).parent, 1).filter {
                Files.isRegularFile(it)
            }.map { File(it.toString()) }.filter {
                it.path != solution.path && it.path.endsWith(".java")
            }.collect(Collectors.toList()).map {
                it.readText().parseJava().tree
            }.map {
                "${it.packageName()}.${it.className()}"
            }

            val kotlinFiles = if (Files.isRegularFile(Paths.get(solution.path))) {
                Files.walk(Paths.get(solution.path).parent).filter { path ->
                    !knownFiles.contains(path.toString()) && Files.isRegularFile(path) && path.toString()
                        .endsWith(".kt")
                }.map { it.toString() }.collect(Collectors.toSet())
            } else {
                setOf<String>()
            }.map {
                ParsedKotlinFile(File(it))
            }.filter {
                it.isQuestioner
            }

            if (solution.autoStarter) {
                solution.extractStarter()
            }

            val javaStarter =
                otherFiles.filter { it.packageName.startsWith("${solution.packageName}.") }
                    .filter { it.starter != null }.let {
                        assert(it.size <= 1) {
                            "Solution ${solution.correct.name} provided multiple files marked as starter code"
                        }
                        it.firstOrNull()
                    }?.also {
                        require(it.path !in usedFiles) { "File $it.path was already used as ${usedFiles[it.path]}" }
                        usedFiles[it.path] = "Starter"
                        myUsedFiles.add(it.path)
                    }
            if (solution.autoStarter) {
                check(javaStarter == null) {
                    "autoStarter set to true but found a file marked as @Starter. Please remove it.\n" +
                        javaStarter!!.path
                }
            }

            val importNames = solution.listedImports.filter { it in byFullName } + neighborImports

            var javaTemplate = File("${solution.path}.hbs").let {
                if (it.exists()) {
                    it
                } else {
                    null
                }
            }?.also {
                require(it.path !in usedFiles) { "File $it.path was already used as ${usedFiles[it.path]}" }
                usedFiles[it.path] = "Template"
                myUsedFiles.add(it.path)
            }?.readText()?.stripPackage()

            var kotlinTemplate = File("${solution.path.replace(".java$".toRegex(), ".kt")}.hbs").let {
                if (it.path != "${solution.path}.hbs" && it.exists()) {
                    it
                } else {
                    null
                }
            }?.also {
                require(it.path !in usedFiles) { "File $it.path was already used as ${usedFiles[it.path]}" }
                usedFiles[it.path] = "Template"
                myUsedFiles.add(it.path)
            }?.readText()?.stripPackage()

            val kotlinSolution = kotlinFiles.find { it.alternateSolution != null && it.description != null }
            if (kotlinFiles.any { it.alternateSolution != null }) {
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
                javaTemplate = solution.extractTemplate() ?: error(
                    "Can't extract Java template"
                )
            }

            if (hasKotlinTemplate && kotlinTemplate == null && solution.wrapWith == null) {
                kotlinTemplate = kotlinSolution!!.extractTemplate() ?: error(
                    "Can't extract Kotlin template"
                )
            }

            val incorrectExamples =
                otherFiles.asSequence().filter { it.packageName.startsWith("${solution.packageName}.") }
                    .filter { it.incorrect != null }
                    .onEach {
                        require(usedFiles[it.path] !in usedFiles) {
                            "File $it.path was already used as ${usedFiles[it.path]}"
                        }
                        usedFiles[it.path] = "Incorrect"
                        myUsedFiles.add(it.path)
                    }.map { it.toIncorrectFile(javaCleanSpec) }.toMutableList().apply {
                        addAll(
                            kotlinFiles.filter { it.incorrect != null }
                                .onEach {
                                    require(it.path !in usedFiles) {
                                        "File $it.path was already used as ${usedFiles[it.path]}"
                                    }
                                    usedFiles[it.path] = "Incorrect"
                                    myUsedFiles.add(it.path)
                                }
                                .map { it.toIncorrectFile(kotlinCleanSpec) }
                        )
                    }

            val alternateSolutions =
                otherFiles.filter { it.packageName.startsWith("${solution.packageName}.") }
                    .filter { it.alternateSolution != null }
                    .onEach {
                        require(it.path !in usedFiles) {
                            "File $it.path was already used as ${usedFiles[it.path]}"
                        }
                        usedFiles[it.path] = "Alternate"
                        myUsedFiles.add(it.path)
                    }.map {
                        it.toAlternateFile(javaCleanSpec)
                    }.toMutableList().apply {
                        addAll(
                            kotlinFiles
                                .filter { it.alternateSolution != null }
                                .onEach {
                                    require(it.path !in usedFiles) {
                                        "File $it.path was already used as ${usedFiles[it.path]}"
                                    }
                                    usedFiles[it.path] = "Correct"
                                    myUsedFiles.add(it.path)
                                }
                                .map { it.toAlternateFile(kotlinCleanSpec) }
                        )
                    }.toList()

            val common = importNames.map {
                usedFiles[byFullName[it]!!.path] = "Common"
                myUsedFiles.add(byFullName[it]!!.path)
                byFullName[it]?.contents?.stripPackage() ?: error("Couldn't find import $it")
            }

            val javaStarterFile = if (solution.autoStarter) {
                solution.extractStarter() ?: error("autoStarter enabled but starter generation failed")
            } else {
                javaStarter?.toStarterFile(javaCleanSpec)?.also {
                    if (it.path in usedFiles) {
                        check(usedFiles[it.path] == "Incorrect" || usedFiles[it.path] == "Starter")
                    }
                }
            }

            var kotlinStarterFile = kotlinFiles.filter { it.starter != null }.also {
                require(it.size <= 1) { "Provided multiple file with Kotlin starter code" }
            }.firstOrNull()?.let {
                require(it.path !in usedFiles || usedFiles[it.path] == "Incorrect") {
                    "File $it.path was already used as ${usedFiles[it.path]}"
                }
                usedFiles[it.path] = "Starter"
                myUsedFiles.add(it.path)
                it.toStarterFile(kotlinCleanSpec)
            }

            if (solution.autoStarter) {
                val autoStarter = kotlinSolution?.extractStarter(solution.wrapWith)
                if (autoStarter != null && kotlinStarterFile != null) {
                    error("autoStarter succeeded but Kotlin starter file found. Please remove it.\n" + kotlinStarterFile.path)
                }
                kotlinStarterFile = autoStarter
            }

            if (solution.wrapWith != null) {
                require(javaTemplate == null && kotlinTemplate == null) {
                    "Can't use both a template and @Wrap"
                }

                // Needed to set imports properly
                solution.clean(javaCleanSpec)

                javaTemplate = """public class ${solution.wrapWith} {
                |  {{{ contents }}}
                |}
                """.trimMargin()
                val javaImports = (solution.usedImports + solution.templateImports).toSet()
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

                    val kotlinImports = (kotlinSolution.usedImports + solution.templateImports).toSet()
                    if (kotlinImports.isNotEmpty()) {
                        kotlinTemplate = kotlinImports.joinToString("\n") { "import $it" } + "\n\n$kotlinTemplate"
                    }
                }
            }

            kotlinStarterFile?.also { incorrectExamples.add(0, it) }
            javaStarterFile?.also { incorrectExamples.add(0, it) }

            if (solution.correct.description in javaDescriptionsByPath) {
                error("Duplicate description: ${solution.path} and ${javaDescriptionsByPath[solution.correct.description]}")
            }
            if (kotlinSolution?.description != null && kotlinSolution.description in kotlinDescriptionsByPath) {
                error("Duplicate description: ${kotlinSolution.path} and ${kotlinDescriptionsByPath[kotlinSolution.description]}")
            }
            javaDescriptionsByPath[solution.correct.description] = solution.path
            kotlinSolution?.description?.let { kotlinDescriptionsByPath[it] = solution.path }

            val (cleanSolution, questionType) = solution.toCleanSolution(javaCleanSpec)

            Question(
                solution.correct.name,
                questionType,
                solution.className,
                Question.Metadata(
                    allContentHash,
                    solution.packageName,
                    solution.correct.version,
                    solution.correct.author,
                    solution.correct.description,
                    kotlinSolution?.description,
                    solution.citation,
                    myUsedFiles,
                    solution.templateImports.toSet(),
                    solution.correct.focused
                ),
                solution.correct.control,
                Question.FlatFile(
                    solution.className,
                    solution.removeImports(importNames).stripPackage(),
                    Question.Language.java,
                    solution.path
                ),
                cleanSolution,
                alternateSolutions,
                incorrectExamples,
                common,
                javaTemplate,
                kotlinTemplate,
                solution.whitelist.toSet(),
                solution.blacklist.toSet(),
                solution.correct.path ?: slugify.slugify(solution.correct.name),
                kotlinSolution?.toAlternateFile(kotlinCleanSpec)
            )
        } catch (e: Exception) {
            throw Exception("Process ${solution.path} failed: $e", e)
        }
    }
    allPaths.filter { !usedFiles.containsKey(it) && !skippedFiles.contains(it) }.forEach {
        println("WARNING: $it will not be included in the build")
    }
    return questions
}

val annotationsToRemove =
    setOf(
        Correct::class.java.simpleName,
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
        ProvideSystemIn::class.java.simpleName
    )
val annotationsToDestroy =
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
        Compare::class.java.simpleName
    )
val annotationsToSnip = setOf(NotNull::class.java.simpleName)

val importsToRemove = annotationsToRemove.map { "edu.illinois.cs.cs125.questioner.lib.$it" }.toSet() +
    "edu.illinois.cs.cs125.questioner.lib.Ignore"
val packagesToRemove = setOf("edu.illinois.cs.cs125.jenisol")

private val emailRegex = Pattern.compile(
    "[a-zA-Z0-9+._%\\-]{1,256}" +
        "@" +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
        "(" +
        "\\." +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
        ")+"
)

fun String.isEmail(): Boolean = emailRegex.matcher(this).matches()

data class CleanSpec(
    val hasTemplate: Boolean = false,
    val wrappedClass: String? = null,
    val importNames: List<String> = listOf()
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

val markdownParser = MarkdownParser(CommonMarkFlavourDescriptor())
