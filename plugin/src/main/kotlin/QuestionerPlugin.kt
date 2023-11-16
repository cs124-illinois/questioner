package edu.illinois.cs.cs125.questioner.plugin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import edu.illinois.cs.cs125.questioner.plugin.save.SaveQuestions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuestionerConfig(val endpoints: List<EndPoint> = listOf()) {
    data class EndPoint(val name: String, val token: String, val url: String)
}

fun Project.javaSourceDir(): File =
    extensions.getByType(JavaPluginExtension::class.java)
        .sourceSets.getByName("main").java.srcDirs.let {
            check(it.size == 1) { "Found multiple source directories: ${it.joinToString(",")}" }
            it.first()!!
        }

@Suppress("unused")
class QuestionerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val config = project.extensions.create("questioner", QuestionerConfigExtension::class.java)
        val configuration =
            project.file(".questioner.yaml").let {
                if (it.exists()) {
                    try {
                        ObjectMapper(YAMLFactory()).apply { registerKotlinModule() }.readValue(it)
                    } catch (e: Exception) {
                        project.logger.warn("Invalid questioner.yaml file.")
                        QuestionerConfig()
                    }
                } else {
                    QuestionerConfig()
                }
            }

        val saveQuestions = project.tasks.register("saveQuestions", SaveQuestions::class.java).get()
        val generateMetatests = project.tasks.register("generateQuestionMetatests", GenerateMetatests::class.java).get()
        generateMetatests.dependsOn(project.tasks.getByName("processResources"))
        project.afterEvaluate {
            project.tasks.getByName("processResources").dependsOn(saveQuestions)

            generateMetatests.seed = config.seed
            generateMetatests.maxMutationCount = config.maxMutationCount

            project.configurations.getByName("runtimeClasspath") { conf ->
                val agentJarPath =
                    conf.resolvedConfiguration.resolvedArtifacts.find {
                        it.moduleVersion.id.group == "com.beyondgrader.resource-agent" &&
                            it.moduleVersion.id.name == "agent"
                    }!!.file.absolutePath
                project.tasks.withType(Test::class.java) {
                    it.jvmArgs("-javaagent:$agentJarPath")
                }
            }
        }
        if (configuration.endpoints.isNotEmpty()) {
            val publishAll = project.tasks.register("publishQuestions").get()
            configuration.endpoints.forEach { (name, token, url) ->
                val publishQuestions =
                    project.tasks.register("${name}PublishQuestions", PublishQuestions::class.java).get()
                publishQuestions.token = token
                publishQuestions.destination = url
                publishQuestions.dependsOn(saveQuestions)
                publishAll.dependsOn(publishQuestions)
            }
        }
        project.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName("main").resources { it.srcDirs(project.layout.buildDirectory.dir("questioner")) }
        project.tasks.register("cleanQuestions", CleanQuestions::class.java)
        project.tasks.register("printSlowQuestions", PrintSlowQuestions::class.java)
        val reconfigureTesting = project.tasks.register("reconfigureTesting", ReconfigureTesting::class.java).get()
        project.tasks.getByName("test").dependsOn(reconfigureTesting)
        project.tasks.getByName("test").mustRunAfter(reconfigureTesting)
        project.tasks.getByName("compileJava").mustRunAfter(reconfigureTesting)
        project.tasks.getByName("compileKotlin").mustRunAfter(reconfigureTesting)
        project.tasks.getByName("jar").mustRunAfter(reconfigureTesting)
        project.tasks.getByName("test").dependsOn(generateMetatests)
        project.tasks.getByName("compileTestKotlin").dependsOn(generateMetatests)
        try {
            project.tasks.getByName("formatKotlinTest").dependsOn(generateMetatests)
        } catch (_: Exception) {
        }
        try {
            project.tasks.getByName("lintKotlinTest").dependsOn(generateMetatests)
        } catch (_: Exception) {
        }
    }
}
