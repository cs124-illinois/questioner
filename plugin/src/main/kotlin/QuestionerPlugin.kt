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
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.KotlinterPlugin
import java.net.URI

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuestionerConfig(val endpoints: List<EndPoint> = listOf()) {
    data class EndPoint(val name: String, val token: String, val url: String, val label: String? = null)
}

// Additional JVM args to disable JIT for consistent memory measurements (calibration phase)
private val noJitJvmArgs = listOf(
    "-XX:-TieredCompilation",
    "-XX:CompileThreshold=100000",
)

@Suppress("unused")
class QuestionerPlugin : Plugin<Project> {
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
        tasks.getByName("saveQuestions")
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

        // Initialize the validation server manager with config values
        ValidationServerManager.initialize(
            project = project,
            commonJvmArgs = commonJvmArgs,
            noJitJvmArgs = noJitJvmArgs,
            rootDir = project.rootProject.projectDir.absolutePath,
            maxMutationCount = config.maxMutationCount,
            retries = config.retries,
            verbose = config.verbose,
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

        // Aggregate saveQuestions task that depends on all subproject saveQuestion tasks
        project.tasks.register("saveQuestions") { saveQuestions ->
            saveQuestions.group = "questioner"
            saveQuestions.description = "Save all question metadata files"
            saveQuestions.dependsOn("buildPackageMap")
            saveQuestions.mustRunAfter("cleanQuestions")

            // Depend on all subproject saveQuestion tasks
            project.subprojects { subproject ->
                if (subproject.name.startsWith("question-")) {
                    saveQuestions.dependsOn(subproject.tasks.named("saveQuestion"))
                }
            }
        }

        project.tasks.register("reconfigureForTesting") {
            project.tasks.getByName("compileJava").enabled = false
            project.tasks.getByName("compileKotlin").enabled = false
            project.tasks.getByName("jar").enabled = false
        }

        project.tasks.register("recollectQuestions", CollectQuestions::class.java) { recollectQuestions ->
            recollectQuestions.dependsOn("saveQuestions")
        }

        // Aggregate validation tasks that depend on all subproject validation tasks
        project.tasks.register("validateQuestions") { task ->
            task.group = "questioner"
            task.description = "Phase 1: Run bootstrap, mutation, and incorrect testing (with JIT)"
            task.dependsOn("saveQuestions")
            task.finalizedBy("recollectQuestions")
            project.subprojects { subproject ->
                if (subproject.name.startsWith("question-")) {
                    task.dependsOn(subproject.tasks.named("validateQuestion"))
                }
            }
        }

        project.tasks.register("calibrateQuestions") { task ->
            task.group = "questioner"
            task.description = "Phase 2: Run calibration (without JIT for consistent memory)"
            task.mustRunAfter("validateQuestions")
            task.finalizedBy("recollectQuestions")
            project.subprojects { subproject ->
                if (subproject.name.startsWith("question-")) {
                    task.dependsOn(subproject.tasks.named("calibrateQuestion"))
                }
            }
        }

        project.tasks.register("testAllQuestions") { task ->
            task.group = "questioner"
            task.dependsOn("validateQuestions", "calibrateQuestions")
            task.description = "Run full validation: phase 1 (with JIT) then phase 2 calibration (without JIT)"
        }

        project.tasks.register("testUnvalidatedQuestions") { task ->
            task.group = "questioner"
            task.dependsOn("validateQuestions", "calibrateQuestions")
            task.description = "Run validation for unvalidated questions (two-phase)"
        }

        // TODO: Add focused question filtering via metadata check
        project.tasks.register("testFocusedQuestions") { task ->
            task.group = "questioner"
            task.dependsOn("validateQuestions", "calibrateQuestions")
            task.description = "Run validation for focused questions only (two-phase)"
        }

        project.tasks.register("collectQuestions", CollectQuestions::class.java) { collectQuestions ->
            collectQuestions.dependsOn("saveQuestions")
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

        val dumpTasks = uploadConfiguration.endpoints.map { endpoint ->
            project.tasks.register("dumpQuestionsTo${endpoint.name}", DumpQuestions::class.java) { dumpQuestions ->
                dumpQuestions.endpoint = endpoint
                dumpQuestions.dependsOn("collectQuestions")
                dumpQuestions.outputs.upToDateWhen { false }
                dumpQuestions.description =
                    "Dump questions that would be published to ${endpoint.name} (${endpoint.url})"
            }.get()
        }

        val printSlowQuestions =
            project.tasks.register("printSlowQuestions", PrintSlowQuestions::class.java) { printSlowQuestions ->
                printSlowQuestions.dependsOn("collectQuestions")
                printSlowQuestions.outputs.upToDateWhen { false }
            }.get()

        val showUpdatedSeeds =
            project.tasks.register("showUpdatedSeeds", ShowUpdatedSeeds::class.java) { showUpdatedSeeds ->
                showUpdatedSeeds.dependsOn("collectQuestions")
                showUpdatedSeeds.outputs.upToDateWhen { false }
            }.get()

        project.afterEvaluate {
            project.finalizeConfiguration(config)

            publishingTasks.forEach { task ->
                task.publishIncludes = config.publishIncludes
                task.ignorePackages = config.ignorePackages
            }
            dumpTasks.forEach { task ->
                task.publishIncludes = config.publishIncludes
                task.ignorePackages = config.ignorePackages
            }

            printSlowQuestions.ignorePackages = config.ignorePackages
            showUpdatedSeeds.ignorePackages = config.ignorePackages
        }
    }
}
