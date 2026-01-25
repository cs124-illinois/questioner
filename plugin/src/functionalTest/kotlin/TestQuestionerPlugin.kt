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

        fun createInitScript(repoPath: String): File {
            return File.createTempFile("init", ".gradle.kts").apply {
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
        }

        "plugin-fixtures should build successfully with combined validation".config(timeout = 600.seconds) {
            val fixturesDir = File("../plugin-fixtures")
            val repoPath = System.getProperty("com.autonomousapps.plugin-under-test.repo")
                ?: error("Missing system property: com.autonomousapps.plugin-under-test.repo")

            val initScript = createInitScript(repoPath)

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

        "plugin-fixtures should build successfully with two-phase validation".config(timeout = 600.seconds) {
            val fixturesDir = File("../plugin-fixtures")
            val repoPath = System.getProperty("com.autonomousapps.plugin-under-test.repo")
                ?: error("Missing system property: com.autonomousapps.plugin-under-test.repo")

            val initScript = createInitScript(repoPath)

            // Clean and run phase 1 (validateQuestions)
            val phase1Result = GradleRunner.create()
                .withProjectDir(fixturesDir)
                .withArguments(
                    "--init-script",
                    initScript.absolutePath,
                    "clean",
                    "validateQuestions",
                    "--stacktrace",
                )
                .forwardOutput()
                .build()

            phase1Result.task(":validateQuestions")?.outcome shouldBe TaskOutcome.SUCCESS

            // Run phase 2 (calibrateQuestions)
            val phase2Result = GradleRunner.create()
                .withProjectDir(fixturesDir)
                .withArguments(
                    "--init-script",
                    initScript.absolutePath,
                    "calibrateQuestions",
                    "--stacktrace",
                )
                .forwardOutput()
                .build()

            phase2Result.task(":calibrateQuestions")?.outcome shouldBe TaskOutcome.SUCCESS

            val questionsJson = File(fixturesDir, "build/questioner/questions.json")
            questionsJson.exists() shouldBe true
        }
    })
