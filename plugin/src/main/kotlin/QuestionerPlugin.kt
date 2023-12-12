package edu.illinois.cs.cs125.questioner.plugin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import edu.illinois.cs.cs125.questioner.lib.VERSION
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuestionerConfig(val endpoints: List<EndPoint> = listOf()) {
    data class EndPoint(val name: String, val token: String, val url: String)
}

open class QuestionerConfigExtension {
    var seed: Int = 124
    var maxMutationCount: Int = 32
    var concurrency: Int = (Runtime.getRuntime().availableProcessors().toDouble() * 0.75).toInt().coerceAtLeast(1)
    var retries: Int = 4
}

private val testFiles = listOf("TestAllQuestions.kt", "TestUnvalidatedQuestions.kt", "TestFocusedQuestions.kt")

@Suppress("unused")
class QuestionerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val config = project.extensions.create("questioner", QuestionerConfigExtension::class.java)

        val saveQuestions = project.tasks.register("saveQuestions", SaveQuestions::class.java).get()

        project.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName("test").java.srcDirs(project.layout.buildDirectory.dir("questioner").get().asFile)

        project.tasks.register("cleanQuestions", Delete::class.java) {
            it.delete(
                project.extensions.getByType(JavaPluginExtension::class.java)
                    .sourceSets.getByName("main").allSource
                    .filter { file -> file.name == ".validation.json" || file.name == "report.html" || file.name == ".question.json" },
            )
        }

        project.tasks.withType(SourceTask::class.java) { sourceTask ->
            sourceTask.exclude("**/.question.json")
            sourceTask.exclude("questions.json", *testFiles.toTypedArray())
            if (sourceTask !is JavaCompile) {
                saveQuestions.mustRunAfter(sourceTask)
            }
        }

        project.tasks.create("testAllQuestions", Test::class.java) { testTask ->
            testTask.setTestNameIncludePatterns(listOf("TestAllQuestions"))
            testTask.outputs.upToDateWhen { false }
        }
        project.tasks.create("testUnvalidatedQuestions", Test::class.java) { testTask ->
            testTask.setTestNameIncludePatterns(listOf("TestUnvalidatedQuestions"))
            testTask.outputs.upToDateWhen { false }
        }
        project.tasks.create("testFocusedQuestions", Test::class.java) { testTask ->
            testTask.setTestNameIncludePatterns(listOf("TestFocusedQuestions"))
            testTask.outputs.upToDateWhen { false }
        }
        project.tasks.getByName("test") { testTask ->
            testTask as Test
            testTask.setTestNameIncludePatterns(listOf("TestUnvalidatedQuestions"))
            testTask.outputs.upToDateWhen { false }
        }

        val collectQuestions = project.tasks.register("collectQuestions", CollectQuestions::class.java).get()
        collectQuestions.dependsOn(saveQuestions)
        collectQuestions.outputs.upToDateWhen { false }

        val generateQuestionTests =
            project.tasks.register("generateQuestionTests", GenerateQuestionTests::class.java).get()
                .also { generateMetatests ->
                    generateMetatests.dependsOn(collectQuestions)
                    project.tasks.getByName("compileTestKotlin").dependsOn(generateMetatests)
                }

        project.afterEvaluate {
            project.configurations.getByName("implementation").dependencies.find { dependency ->
                dependency.group == "org.cs124" && dependency.name == "questioner"
            }?.let {
                error("Found explicit questioner library dependency. Please remove it, since it is automatically added by the plugin.")
            }
            project.dependencies.add("implementation", project.dependencies.create("org.cs124:questioner:$VERSION"))

            generateQuestionTests.seed = config.seed
            generateQuestionTests.maxMutationCount = config.maxMutationCount
            generateQuestionTests.concurrency = config.concurrency
            generateQuestionTests.retries = config.retries

            project.tasks.withType(Test::class.java).forEach { testTask ->
                testTask.dependsOn(generateQuestionTests)
                testTask.mustRunAfter(generateQuestionTests)
            }

            val agentJarPath = project.configurations.getByName("runtimeClasspath")
                .resolvedConfiguration.resolvedArtifacts
                .find { artifact ->
                    artifact.moduleVersion.id.group == "com.beyondgrader.resource-agent" &&
                        artifact.moduleVersion.id.name == "agent"
                }!!.file.absolutePath
            project.tasks.withType(Test::class.java) { testTask ->
                testTask.jvmArgs("-javaagent:$agentJarPath")
            }
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
        if (uploadConfiguration.endpoints.isNotEmpty()) {
            val publishAll = project.tasks.register("publishQuestions").get()
            publishAll.outputs.upToDateWhen { false }
            uploadConfiguration.endpoints.forEach { (name, token, url) ->
                val publishQuestions =
                    project.tasks.register("publishQuestionsTo$name", PublishQuestions::class.java).get()
                publishQuestions.token = token
                publishQuestions.destination = url
                publishQuestions.dependsOn(collectQuestions)
                publishQuestions.outputs.upToDateWhen { false }
                publishQuestions.description = "Publish questions to $name"
                publishAll.dependsOn(publishQuestions)
            }
        }
        project.tasks.register("printSlowQuestions", PrintSlowQuestions::class.java) { printSlowQuestions ->
            printSlowQuestions.dependsOn("collectQuestions")
            printSlowQuestions.outputs.upToDateWhen { false }
        }
    }
}
