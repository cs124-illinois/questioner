package edu.illinois.cs.cs125.questioner.plugin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatExtension
import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatPlugin
import edu.illinois.cs.cs125.questioner.lib.VERSION
import io.github.cdimascio.dotenv.Dotenv
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.KotlinterPlugin
import java.net.URI
import java.util.Locale

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuestionerConfig(val endpoints: List<EndPoint> = listOf()) {
    data class EndPoint(val name: String, val token: String, val url: String, val label: String? = null)
}

val dotenv: Dotenv = Dotenv.configure().ignoreIfMissing().load()

private val testFiles = listOf("TestAllQuestions.kt", "TestUnvalidatedQuestions.kt", "TestFocusedQuestions.kt")

@Suppress("unused")
class QuestionerPlugin : Plugin<Project> {
    private fun Project.configurePlugins() {
        pluginManager.apply("java")
        extensions.getByType(JavaPluginExtension::class.java).apply {
            toolchain.apply {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            sourceSets.getByName("test").java.srcDirs(layout.buildDirectory.dir("questioner").get().asFile)
        }

        buildscript.dependencies.add("classpath", "org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        pluginManager.apply("checkstyle")

        pluginManager.apply(GoogleJavaFormatPlugin::class.java)
        extensions.getByType(GoogleJavaFormatExtension::class.java).apply {
            toolVersion = "1.22.0"
        }

        pluginManager.apply(DetektPlugin::class.java)
        extensions.getByType(DetektExtension::class.java).apply {
            buildUponDefaultConfig = true
        }

        pluginManager.apply(KotlinterPlugin::class.java)
    }

    private fun Project.finalizeConfiguration() {
        project.configurations.getByName("implementation").dependencies.find { dependency ->
            (dependency.group == "org.cs124" && dependency.name == "questioner") || (dependency.group == "org.cs124.questioner")
        }?.let {
            error("Found explicit questioner library dependency. Please remove it, since it is automatically added by the plugin.")
        }
        project.dependencies.add("implementation", project.dependencies.create("org.cs124.questioner:lib:$VERSION"))

        tasks.withType(SourceTask::class.java) { sourceTask ->
            sourceTask.exclude("**/.question.json")
            sourceTask.exclude("**/report.html")
            sourceTask.exclude("questions.json", "packageMap.json", *testFiles.toTypedArray())
        }
        tasks.withType(Test::class.java).forEach { testTask ->
            testTask.dependsOn("generateQuestionTests")
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
        tasks.withType(Test::class.java) { testTask ->
            testTask.useJUnitPlatform()
            testTask.enableAssertions = true
            testTask.environment["JEED_USE_CACHE"] = true
            testTask.jvmArgs(
                "-ea", "--enable-preview", "-Dfile.encoding=UTF-8", "-Djava.security.manager=allow",
                "-XX:+UseZGC", "-XX:ZCollectionInterval=8", "-XX:-OmitStackTraceInFastThrow",
                "-Xmx4G",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                "--add-exports", "java.management/sun.management=ALL-UNNAMED",
                "-javaagent:$agentJarPath",
            )
            testTask.outputs.upToDateWhen { false }
            testTask.dependsOn("reconfigureForTesting")
            testTask.finalizedBy("recollectQuestions")
        }

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
            cleanQuestions.delete(
                project.extensions.getByType(JavaPluginExtension::class.java)
                    .sourceSets.getByName("main").allSource
                    .filter { file -> file.name == ".validation.json" || file.name == "report.html" || file.name == ".question.json" },
            )
        }
        project.tasks.getByName("clean").dependsOn("cleanQuestions")

        project.tasks.register("saveQuestions", SaveQuestions::class.java) { saveQuestions ->
            saveQuestions.dependsOn("buildPackageMap")
            saveQuestions.mustRunAfter("cleanQuestions")
        }

        project.tasks.register("reconfigureForTesting") {
            project.tasks.getByName("compileJava").enabled = false
            project.tasks.getByName("compileKotlin").enabled = false
            project.tasks.getByName("jar").enabled = false
        }

        project.tasks.register("recollectQuestions", CollectQuestions::class.java)

        listOf("testAllQuestions", "testUnvalidatedQuestions", "testFocusedQuestions").map { testName ->
            project.tasks.create(testName, Test::class.java) { testTask ->
                testTask.setTestNameIncludePatterns(listOf(testName.capitalized()))
            }
        }
        project.tasks.getByName("test") { testTask ->
            testTask as Test
            testTask.setTestNameIncludePatterns(listOf("TestUnvalidatedQuestions"))
        }

        project.tasks.register("collectQuestions", CollectQuestions::class.java) { collectQuestions ->
            collectQuestions.dependsOn("saveQuestions")
            collectQuestions.outputs.upToDateWhen { false }
        }

        project.tasks.register("generateQuestionTests", GenerateQuestionTests::class.java) { generateQuestionTests ->
            generateQuestionTests.dependsOn("collectQuestions")
            project.tasks.getByName("compileTestKotlin").dependsOn(generateQuestionTests)
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
                publishQuestions.dependsOn("collectQuestions")
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

        project.tasks.register("printSlowQuestions", PrintSlowQuestions::class.java) { printSlowQuestions ->
            printSlowQuestions.dependsOn("collectQuestions")
            printSlowQuestions.outputs.upToDateWhen { false }
        }

        project.tasks.register("showUpdatedSeeds", ShowUpdatedSeeds::class.java) { showUpdatedSeeds ->
            showUpdatedSeeds.dependsOn("collectQuestions")
            showUpdatedSeeds.outputs.upToDateWhen { false }
        }

        project.afterEvaluate {
            project.finalizeConfiguration()

            project.tasks.withType(GenerateQuestionTests::class.java) { generateQuestionTests ->
                generateQuestionTests.maxMutationCount = config.maxMutationCount
                generateQuestionTests.retries = config.retries
                generateQuestionTests.verbose = config.verbose
                generateQuestionTests.shuffleTests = config.shuffleTests
            }

            publishingTasks.forEach { task ->
                task.publishIncludes = config.publishIncludes
                task.ignorePackages = config.ignorePackages
            }
            dumpTasks.forEach { task ->
                task.publishIncludes = config.publishIncludes
                task.ignorePackages = config.ignorePackages
            }
        }
    }
}

private fun String.capitalized() = replaceFirstChar { firstChar ->
    when {
        firstChar.isLowerCase() -> firstChar.titlecase(Locale.getDefault())
        else -> firstChar.toString()
    }
}
