@file:Suppress("TooManyFunctions")

package edu.illinois.cs.cs125.questioner.plugin.save

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import edu.illinois.cs.cs125.jenisol.core.*
import edu.illinois.cs.cs125.questioner.lib.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
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

private val moshi = Moshi.Builder().build()

@JsonClass(generateAdapter = true)
data class PathFile(val path: String, val contents: String)

@Suppress("unused")
abstract class SaveQuestions : DefaultTask() {
    init {
        group = "Build"
        description = "Save questions to JSON."
    }

    @InputFiles
    val inputFiles: FileCollection = project.convention.getPlugin(JavaPluginConvention::class.java)
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
    solutions.map { it.correct!!.name }.groupingBy { it }.eachCount().filter { it.value > 1 }.also { duplicates ->
        if (duplicates.isNotEmpty()) {
            error("Duplicate questions found: ${duplicates.keys}")
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

            val javaCleanSpec = CleanSpec(javaTemplate != null, solution.wrapWith, importNames)
            val kotlinCleanSpec = CleanSpec(kotlinTemplate != null, solution.wrapWith, importNames)

            val incorrectExamples =
                otherFiles.filter { it.packageName.startsWith("${solution.packageName}.") }
                    .filter { it.incorrect != null }
                    .onEach {
                        if (it.path in usedFiles) {
                            require(usedFiles[it.path] == "Starter") {
                                "File $it.path was already used as ${usedFiles[it.path]}"
                            }
                        }
                        usedFiles[it.path] = "Incorrect"
                        myUsedFiles.add(it.path)
                    }
                    .also {
                        require(it.isNotEmpty()) {
                            "Solution ${solution.correct.name} (${solution.path}) did not provide any counterexamples " +
                                    "annotated with @Incorrect"
                        }
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

            val javaStarterFile = javaStarter?.toStarterFile(javaCleanSpec)

            val kotlinStarterFile = kotlinFiles.filter { it.starter != null }.also {
                require(it.size <= 1) { "Provided multiple file with Kotlin starter code" }
            }.firstOrNull()?.let {
                require(it.path !in usedFiles || usedFiles[it.path] == "Incorrect") {
                    "File $it.path was already used as ${usedFiles[it.path]}"
                }
                usedFiles[it.path] = "Starter"
                myUsedFiles.add(it.path)
                it.toStarterFile(kotlinCleanSpec)
            }

            val kotlinSolution = kotlinFiles.find { it.alternateSolution != null && it.description != null }

            if (solution.wrapWith != null) {
                require(javaTemplate == null && kotlinTemplate == null) {
                    "Can't use both a template and @Wrap"
                }

                solution.clean(javaCleanSpec)

                javaTemplate = """public class ${solution.wrapWith} {
                |  {{{ contents }}}
                |}
            """.trimMargin()
                if (solution.usedImports.isNotEmpty()) {
                    javaTemplate = solution.usedImports.joinToString("\n") { "import $it;" } + "\n\n$javaTemplate"
                }

                kotlinTemplate = """class ${solution.wrapWith} {
                |  {{{ contents }}}
                |}
            """.trimMargin()

                if (kotlinSolution?.usedImports?.isNotEmpty() == true) {
                    kotlinTemplate =
                        kotlinSolution.usedImports.joinToString("\n") { "import $it" } + "\n\n$kotlinTemplate"
                }
            }

            Question(
                solution.correct.name,
                solution.className,
                Question.Metadata(
                    allContentHash,
                    solution.packageName,
                    solution.correct.version,
                    solution.correct.author,
                    solution.correct.description,
                    solution.correct.points,
                    solution.correct.timeoutMultiplier,
                    solution.correct.minTimeout,
                    solution.correct.mutate,
                    solution.correct.checkstyle,
                    solution.correct.solutionThrows,
                    solution.correct.maxTestCount,
                    solution.correct.minTestCount,
                    kotlinSolution?.description,
                    solution.citation,
                    myUsedFiles
                ),
                Question.FlatFile(
                    solution.className,
                    solution.removeImports(importNames).stripPackage(),
                    Question.Language.java
                ),
                solution.toCleanSolution(javaCleanSpec),
                alternateSolutions,
                incorrectExamples,
                common,
                javaStarterFile,
                kotlinStarterFile,
                javaTemplate,
                kotlinTemplate,
                solution.whitelist.toSet(),
                solution.blacklist.toSet()
            )
        } catch (e: Exception) {
            throw Exception("Problem parsing ${solution.path}: $e")
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
        DesignOnly::class.java.simpleName,
        Wrap::class.java.simpleName,
        AlsoCorrect::class.java.simpleName
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
        CheckSource::class.java.simpleName,
        Ignore::class.java.simpleName,
        Compare::class.java.simpleName
    )

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
)

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
