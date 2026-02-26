package edu.illinois.cs.cs125.questioner.plugin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatExtension
import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatPlugin
import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.dotenv
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.KotlinterPlugin
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Paths
import javax.inject.Inject

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuestionerConfig(val endpoints: List<EndPoint> = listOf()) {
    data class EndPoint(val name: String, val token: String, val url: String, val label: String? = null)
}

// Additional JVM args to disable JIT for consistent memory measurements (calibration phase)
private val noJitJvmArgs = listOf(
    "-XX:-TieredCompilation",
    "-XX:CompileThreshold=100000",
)

/**
 * Build service that listens for task completion events to track UP-TO-DATE tasks.
 * This replaces the deprecated TaskExecutionGraph.afterTask() API for configuration cache compatibility.
 */
abstract class TaskCompletionService :
    BuildService<BuildServiceParameters.None>,
    OperationCompletionListener {
    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) return
        if (event.result !is TaskSkippedResult) return

        // Task path format is ":question-HASH:taskName"
        val taskPath = event.descriptor.taskPath
        val parts = taskPath.split(":")
        if (parts.size < 3) return

        val projectName = parts[1]
        val taskName = parts[2]

        if (!projectName.startsWith("question-")) return

        when (taskName) {
            "parse" -> ParseProgressManager.getInstance()?.taskSkipped()
            "validate" -> ValidateProgressManager.getInstance()?.taskSkipped()
        }
    }
}

@Suppress("unused")
class QuestionerPlugin @Inject constructor(
    private val buildEventsListenerRegistry: BuildEventsListenerRegistry,
) : Plugin<Project> {
    private fun Project.configurePlugins() {
        pluginManager.apply("java")
        extensions.getByType(JavaPluginExtension::class.java).apply {
            toolchain.apply {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        pluginManager.apply("org.jetbrains.kotlin.jvm")

        pluginManager.apply("checkstyle")

        pluginManager.apply(GoogleJavaFormatPlugin::class.java)
        extensions.getByType(GoogleJavaFormatExtension::class.java).apply {
            toolVersion = "1.28.0"
        }

        pluginManager.apply(DetektPlugin::class.java)
        extensions.getByType(DetektExtension::class.java).apply {
            buildUponDefaultConfig = true
        }

        pluginManager.apply(KotlinterPlugin::class.java)
    }

    private fun Project.finalizeConfiguration(config: QuestionerConfigExtension) {
        project.configurations.getByName("implementation").dependencies.find { dependency ->
            (dependency.group == "org.cs124" && dependency.name == "questioner") || (dependency.group == "org.cs124.questioner")
        }?.let {
            error("Found explicit questioner library dependency. Please remove it, since it is automatically added by the plugin.")
        }
        project.dependencies.add("implementation", project.dependencies.create("org.cs124.questioner:lib:$VERSION"))

        tasks.withType(SourceTask::class.java) { sourceTask ->
            // Note: .question.json no longer written to source tree (now in build/questioner/questions/)
            sourceTask.exclude("**/report.html")
            sourceTask.exclude("questions.json", "packageMap.json")
        }

        tasks.withType(KotlinCompile::class.java) { kompileTask ->
            kompileTask.compilerOptions.apply {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }

        tasks.getByName("check")
            .dependsOn("detekt", "checkstyleMain", "googleJavaFormat", "lintKotlinMain", "formatKotlinMain")
        tasks.getByName("checkstyleMain").mustRunAfter("googleJavaFormat")
        tasks.getByName("parse")
            .mustRunAfter("detekt", "checkstyleMain", "googleJavaFormat", "lintKotlinMain", "formatKotlinMain")

        project.tasks.getByName("compileJava").mustRunAfter("reconfigureForTesting")
        project.tasks.getByName("compileKotlin").mustRunAfter("reconfigureForTesting")
        project.tasks.getByName("jar").mustRunAfter("reconfigureForTesting")

        val agentJarPath = configurations.getByName("runtimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .find { artifact ->
                artifact.moduleVersion.id.group == "com.beyondgrader.resource-agent" &&
                    artifact.moduleVersion.id.name == "agent"
            }!!.file.absolutePath

        // Allow heap size to be configured via environment variable or .env file (default: 1G)
        val heapSize = dotenv["QUESTIONER_HEAP_SIZE"] ?: "1G"

        // Common JVM args for all test tasks (with JIT enabled for speed)
        val commonJvmArgs = listOf(
            "-ea", "--enable-preview", "-Dfile.encoding=UTF-8", "-Djava.security.manager=allow",
            "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:-OmitStackTraceInFastThrow",
            "-XX:+UnlockExperimentalVMOptions", "-XX:-VMContinuations",
            "-Xmx$heapSize",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-exports", "java.management/sun.management=ALL-UNNAMED",
            "-Dslf4j.internal.verbosity=WARN",
            "-javaagent:$agentJarPath",
        )

        // Common configuration for all test tasks
        tasks.withType(Test::class.java) { testTask ->
            testTask.useJUnitPlatform()
            testTask.enableAssertions = true
            testTask.jvmArgs(commonJvmArgs)
            testTask.logging.captureStandardError(LogLevel.DEBUG)
        }

        // Concurrency for validation (how many questions to validate in parallel)
        val validationConcurrency = dotenv["QUESTIONER_VALIDATION_CONCURRENCY"]?.toIntOrNull() ?: 8

        // Count total question subprojects
        val totalQuestions = project.subprojects.count { it.name.startsWith("question-") }

        // Get ProgressLoggerFactory for progress reporting
        val serviceRegistry = (project as org.gradle.api.internal.project.ProjectInternal).services
        val progressLoggerFactory = serviceRegistry.get(ProgressLoggerFactory::class.java)

        // Initialize parse progress manager (fresh instance each build)
        ParseProgressManager.initialize(progressLoggerFactory, totalQuestions)

        // Initialize validate progress manager (fresh instance each build)
        ValidateProgressManager.initialize(progressLoggerFactory, totalQuestions)

        // Register build service to track UP-TO-DATE tasks (configuration cache compatible)
        val taskCompletionService = project.gradle.sharedServices.registerIfAbsent(
            "taskCompletionService",
            TaskCompletionService::class.java,
        ) {}
        buildEventsListenerRegistry.onTaskCompletion(taskCompletionService)

        // Check if servers should be restarted (and stopped on exit)
        val restartServers = project.hasProperty("restartServers")

        // Initialize the validation server manager with config values
        ValidationServerManager.initialize(
            project = project,
            commonJvmArgs = commonJvmArgs,
            noJitJvmArgs = noJitJvmArgs,
            rootDir = project.rootProject.projectDir.absolutePath,
            maxMutationCount = config.maxMutationCount,
            retries = config.retries,
            verbose = config.verbose,
            concurrency = validationConcurrency,
            totalQuestions = totalQuestions,
            restartServers = restartServers,
        )

        configurations.getByName("checkstyle").apply {
            resolutionStrategy.capabilitiesResolution.withCapability("com.google.collections:google-collections") {
                it.select("com.google.guava:guava:0")
            }
        }
    }

    override fun apply(project: Project) {
        project.configurePlugins()

        val config = project.extensions.create("questioner", QuestionerConfigExtension::class.java)

        project.repositories.add(project.repositories.mavenCentral())
        project.repositories.add(project.repositories.mavenLocal())
        project.repositories.add(
            project.repositories.maven { mavenRepository ->
                mavenRepository.url = URI("https://maven.codeawakening.com")
            },
        )

        project.tasks.register("checkQuestionerVersion", CheckQuestionerVersion::class.java) { checkQuestionerVersion ->
            checkQuestionerVersion.outputs.upToDateWhen { false }
        }

        project.tasks.register("buildPackageMap", BuildPackageMap::class.java) { buildPackageMap ->
            buildPackageMap.dependsOn("checkQuestionerVersion")
        }

        project.tasks.register("cleanQuestions", Delete::class.java) { cleanQuestions ->
            // Clean build directory artifacts
            cleanQuestions.delete(
                project.layout.buildDirectory.dir("questioner/questions"),
            )
            // Clean validation artifacts from source tree (still written there)
            cleanQuestions.delete(
                project.extensions.getByType(JavaPluginExtension::class.java)
                    .sourceSets.getByName("main").allSource
                    .filter { file -> file.name == ".validation.json" || file.name == "report.html" },
            )
        }
        project.tasks.getByName("clean").dependsOn("cleanQuestions")

        // Apply QuestionPlugin to all question subprojects
        project.subprojects { subproject ->
            if (subproject.name.startsWith("question-")) {
                subproject.pluginManager.apply(QuestionPlugin::class.java)
            }
        }

        // Aggregate parse task that depends on all subproject parse tasks
        project.tasks.register("parse") { parseTask ->
            parseTask.group = "questioner"
            parseTask.description = "Parse all question files"
            parseTask.dependsOn("buildPackageMap")
            parseTask.mustRunAfter("cleanQuestions")

            // Depend on all subproject parse tasks
            project.subprojects { subproject ->
                if (subproject.name.startsWith("question-")) {
                    parseTask.dependsOn(subproject.tasks.named("parse"))
                }
            }

            // Finish progress bar after all parse tasks complete
            parseTask.doLast {
                ParseProgressManager.getInstance()?.finish()
            }
        }

        project.tasks.register("reconfigureForTesting") {
            project.tasks.getByName("compileJava").enabled = false
            project.tasks.getByName("compileKotlin").enabled = false
            project.tasks.getByName("jar").enabled = false
        }

        project.tasks.register("recollectQuestions", CollectQuestions::class.java) { recollectQuestions ->
            recollectQuestions.dependsOn("parse")
            recollectQuestions.mustRunAfter("validationReport")
        }

        // Shutdown validation servers
        project.tasks.register("shutdownValidationServers") { task ->
            task.mustRunAfter("validationReport", "recollectQuestions")
            task.doLast {
                ValidationServerManager.getInstance(project)?.shutdown()
            }
        }

        // Print validation report and fail if there were failures
        project.tasks.register("validationReport") { task ->
            task.doLast {
                // Finish progress bar before printing report
                ValidateProgressManager.getInstance()?.finish()

                val manager = ValidationServerManager.getInstance(project)
                if (manager != null) {
                    val success = manager.printReport()
                    if (!success) {
                        throw RuntimeException("Validation failed for one or more questions")
                    }
                }
            }
        }

        // Validate unvalidated questions (server skips already-validated)
        project.tasks.register("validate") { task ->
            task.group = "questioner"
            task.description = "Validate unvalidated questions"
            task.dependsOn("parse")
            task.finalizedBy("validationReport", "recollectQuestions")

            // Shutdown servers after validation if restartServers flag is set
            if (project.hasProperty("restartServers")) {
                task.finalizedBy("shutdownValidationServers")
            }

            // Get filter patterns from project properties
            val filterPattern = if (project.hasProperty("filter")) {
                project.property("filter") as String
            } else {
                null
            }
            val authorFilter = if (project.hasProperty("author")) {
                project.property("author") as String
            } else {
                null
            }

            // Get discovered questions from settings phase
            @Suppress("UNCHECKED_CAST")
            val discoveredQuestions = project.extensions.extraProperties.let { extra ->
                if (extra.has("questioner.discoveredQuestions")) {
                    extra.get("questioner.discoveredQuestions") as? List<DiscoveredQuestion>
                } else {
                    null
                }
            } ?: emptyList()

            // Build set of hashes to include (null means include all)
            val includedHashes: Set<String>? = if (filterPattern != null || authorFilter != null) {
                val glob = filterPattern?.let {
                    FileSystems.getDefault().getPathMatcher("glob:$it")
                }
                discoveredQuestions.filter { q ->
                    val matchesFilter = glob?.let { matcher ->
                        matcher.matches(Paths.get(q.slug)) ||
                            matcher.matches(Paths.get(q.fullSlug)) ||
                            matcher.matches(Paths.get(q.correctFile.path))
                    } ?: true
                    val matchesAuthor = authorFilter?.let { q.author == it } ?: true
                    matchesFilter && matchesAuthor
                }.map { it.hash }.toSet()
            } else {
                null
            }

            project.subprojects { subproject ->
                if (subproject.name.startsWith("question-")) {
                    val hash = subproject.name.removePrefix("question-")
                    if (includedHashes == null || hash in includedHashes) {
                        task.dependsOn(subproject.tasks.named("validate"))
                    }
                }
            }
        }

        // Validate all questions regardless of status
        project.tasks.register("validateAll") { task ->
            task.group = "questioner"
            task.description = "Validate all questions (re-validates already validated)"
            task.dependsOn("parse")
            task.finalizedBy("validationReport", "recollectQuestions")

            // Shutdown servers after validation if restartServers flag is set
            if (project.hasProperty("restartServers")) {
                task.finalizedBy("shutdownValidationServers")
            }

            // Get filter patterns from project properties
            val filterPattern = if (project.hasProperty("filter")) {
                project.property("filter") as String
            } else {
                null
            }
            val authorFilter = if (project.hasProperty("author")) {
                project.property("author") as String
            } else {
                null
            }

            // Get discovered questions from settings phase
            @Suppress("UNCHECKED_CAST")
            val discoveredQuestions = project.extensions.extraProperties.let { extra ->
                if (extra.has("questioner.discoveredQuestions")) {
                    extra.get("questioner.discoveredQuestions") as? List<DiscoveredQuestion>
                } else {
                    null
                }
            } ?: emptyList()

            // Build set of hashes to include (null means include all)
            val includedHashes: Set<String>? = if (filterPattern != null || authorFilter != null) {
                val glob = filterPattern?.let {
                    FileSystems.getDefault().getPathMatcher("glob:$it")
                }
                discoveredQuestions.filter { q ->
                    val matchesFilter = glob?.let { matcher ->
                        matcher.matches(Paths.get(q.slug)) ||
                            matcher.matches(Paths.get(q.fullSlug)) ||
                            matcher.matches(Paths.get(q.correctFile.path))
                    } ?: true
                    val matchesAuthor = authorFilter?.let { q.author == it } ?: true
                    matchesFilter && matchesAuthor
                }.map { it.hash }.toSet()
            } else {
                null
            }

            project.subprojects { subproject ->
                if (subproject.name.startsWith("question-")) {
                    val hash = subproject.name.removePrefix("question-")
                    if (includedHashes == null || hash in includedHashes) {
                        task.dependsOn(subproject.tasks.named("validate"))
                    }
                }
            }
            // TODO: Pass force flag to server to re-validate
        }

        // Validate only focused questions
        project.tasks.register("validateFocused") { task ->
            task.group = "questioner"
            task.description = "Validate focused questions only"
            task.dependsOn("parse")
            task.finalizedBy("validationReport", "recollectQuestions")

            // Shutdown servers after validation if restartServers flag is set
            if (project.hasProperty("restartServers")) {
                task.finalizedBy("shutdownValidationServers")
            }

            // Get filter patterns from project properties
            val filterPattern = if (project.hasProperty("filter")) {
                project.property("filter") as String
            } else {
                null
            }
            val authorFilter = if (project.hasProperty("author")) {
                project.property("author") as String
            } else {
                null
            }

            // Get discovered questions from settings phase
            @Suppress("UNCHECKED_CAST")
            val discoveredQuestions = project.extensions.extraProperties.let { extra ->
                if (extra.has("questioner.discoveredQuestions")) {
                    extra.get("questioner.discoveredQuestions") as? List<DiscoveredQuestion>
                } else {
                    null
                }
            } ?: emptyList()

            // Build set of hashes to include (null means include all)
            val includedHashes: Set<String>? = if (filterPattern != null || authorFilter != null) {
                val glob = filterPattern?.let {
                    FileSystems.getDefault().getPathMatcher("glob:$it")
                }
                discoveredQuestions.filter { q ->
                    val matchesFilter = glob?.let { matcher ->
                        matcher.matches(Paths.get(q.slug)) ||
                            matcher.matches(Paths.get(q.fullSlug)) ||
                            matcher.matches(Paths.get(q.correctFile.path))
                    } ?: true
                    val matchesAuthor = authorFilter?.let { q.author == it } ?: true
                    matchesFilter && matchesAuthor
                }.map { it.hash }.toSet()
            } else {
                null
            }

            // TODO: Filter to only focused question subprojects
            project.subprojects { subproject ->
                if (subproject.name.startsWith("question-")) {
                    val hash = subproject.name.removePrefix("question-")
                    if (includedHashes == null || hash in includedHashes) {
                        task.dependsOn(subproject.tasks.named("validate"))
                    }
                }
            }
        }

        project.tasks.register("collectQuestions", CollectQuestions::class.java) { collectQuestions ->
            collectQuestions.dependsOn("parse")
            collectQuestions.outputs.upToDateWhen { false }
        }

        val uploadConfiguration = project.file(".questioner.yaml").let { questionerConfigFile ->
            if (questionerConfigFile.exists()) {
                try {
                    ObjectMapper(YAMLFactory()).apply { registerKotlinModule() }.readValue(questionerConfigFile)
                } catch (e: Exception) {
                    project.logger.warn("Invalid questioner.yaml file.")
                    QuestionerConfig()
                }
            } else {
                QuestionerConfig()
            }
        }

        val publishingTasks = uploadConfiguration.endpoints.map { endpoint ->
            project.tasks.register(
                "publishQuestionsTo${endpoint.name}",
                PublishQuestions::class.java,
            ) { publishQuestions ->
                publishQuestions.endpoint = endpoint
                publishQuestions.dependsOn("collectQuestions", "recollectQuestions")
                publishQuestions.outputs.upToDateWhen { false }
                publishQuestions.description = "Publish questions to ${endpoint.name} (${endpoint.url})"
            }.get()
        }

        if (uploadConfiguration.endpoints.isNotEmpty()) {
            project.tasks.register("publishQuestions") { publishAll ->
                if (uploadConfiguration.endpoints.isNotEmpty()) {
                    publishAll.dependsOn(publishingTasks)
                } else {
                    publishAll.doLast {
                        error("No publishing configuration found. Please add a .questioner.yaml file to the root of your repository.")
                    }
                }
                publishAll.outputs.upToDateWhen { false }
            }
        }

        project.afterEvaluate {
            // Add external source directories to the Java source set so compilation and linting see them
            @Suppress("UNCHECKED_CAST")
            val sourceDirs = project.extensions.extraProperties.let { extra ->
                if (extra.has("questioner.sourceDirs")) {
                    extra.get("questioner.sourceDirs") as? List<File>
                } else {
                    null
                }
            }
            if (sourceDirs != null) {
                val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java)
                    .sourceSets.getByName("main")
                val defaultDir = File(project.projectDir, "src/main/java")
                sourceDirs.filter { it != defaultDir }.forEach { dir ->
                    mainSourceSet.java.srcDir(dir)
                }
            }

            project.finalizeConfiguration(config)

            publishingTasks.forEach { task ->
                task.publishIncludes = config.publishIncludes
                task.ignorePackages = config.ignorePackages
            }
        }
    }
}
