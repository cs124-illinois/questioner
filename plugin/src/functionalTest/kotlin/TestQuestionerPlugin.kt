package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.time.Duration.Companion.seconds

class TestQuestionerPlugin :
    StringSpec({

        fun createInitScript(repoPath: String): File = File.createTempFile("init", ".gradle.kts").apply {
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
                                if (requested.id.id == "org.cs124.questioner" ||
                                    requested.id.id == "org.cs124.questioner.settings" ||
                                    requested.id.id == "org.cs124.questioner.question") {
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

        "plugin-fixtures should validate successfully".config(timeout = 600.seconds) {
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
                    "validate",
                    "--stacktrace",
                )
                .forwardOutput()
                .build()

            result.task(":validate")?.outcome shouldBe TaskOutcome.SUCCESS

            val questionsJson = File(fixturesDir, "build/questioner/questions.json")
            questionsJson.exists() shouldBe true

            // Verify all questions are fully validated (both phases complete)
            val questions = questionsJson.loadQuestionList()
            questions.size shouldBeGreaterThan 0
            questions.count { it.phase1Completed } shouldBe questions.size
            questions.count { it.validated } shouldBe questions.size

            // Verify calibration data is present
            questions.forEach { question ->
                val settings = question.testingSettings
                check(settings != null) { "Question ${question.published.name} missing testingSettings" }
                check(settings.executionCountLimit != null) { "Question ${question.published.name} missing executionCountLimit" }
                check(settings.allocationLimit != null) { "Question ${question.published.name} missing allocationLimit" }
            }

            // Copy to test resources for lib and server modules
            listOf(
                "../lib/src/test/resources/questions.json",
                "../server/src/test/resources/questions.json",
            ).forEach { path ->
                val destination = File(path)
                questionsJson.copyTo(destination, overwrite = true)
                destination.exists() shouldBe true
            }
        }
    })
