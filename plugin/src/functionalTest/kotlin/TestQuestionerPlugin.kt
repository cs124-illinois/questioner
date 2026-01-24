package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.VERSION
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.time.Duration.Companion.seconds

class TestQuestionerPlugin :
    StringSpec({
        "plugin-fixtures should build successfully".config(timeout = 600.seconds) {
            val fixturesDir = File("../plugin-fixtures")
            val repoPath = System.getProperty("com.autonomousapps.plugin-under-test.repo")
                ?: error("Missing system property: com.autonomousapps.plugin-under-test.repo")

            // Create an init script to inject the repo and version before settings are evaluated
            val initScript = File.createTempFile("init", ".gradle.kts").apply {
                deleteOnExit()
                writeText(
                    """
                beforeSettings {
                    pluginManagement {
                        repositories {
                            maven(url = uri("$repoPath"))
                            mavenLocal()
                            mavenCentral()
                            maven(url = uri("https://maven.codeawakening.com"))
                            gradlePluginPortal()
                        }
                        resolutionStrategy {
                            eachPlugin {
                                if (requested.id.id == "org.cs124.questioner") {
                                    useVersion("$VERSION")
                                }
                            }
                        }
                    }
                }
                allprojects {
                    repositories {
                        maven(url = uri("$repoPath"))
                        mavenLocal()
                        mavenCentral()
                        maven(url = uri("https://maven.codeawakening.com"))
                    }
                }
                    """.trimIndent(),
                )
            }

            val result = GradleRunner.create()
                .withProjectDir(fixturesDir)
                .withArguments(
                    "--init-script",
                    initScript.absolutePath,
                    "clean",
                    "testAllQuestions",
                    "--stacktrace",
                )
                .forwardOutput()
                .build()

            result.task(":testAllQuestions")?.outcome shouldBe TaskOutcome.SUCCESS

            val questionsJson = File(fixturesDir, "build/questioner/questions.json")
            questionsJson.exists() shouldBe true

            // Copy to lib test resources
            val destination = File("../lib/src/test/resources/questions.json")
            questionsJson.copyTo(destination, overwrite = true)
            destination.exists() shouldBe true
        }
    })
