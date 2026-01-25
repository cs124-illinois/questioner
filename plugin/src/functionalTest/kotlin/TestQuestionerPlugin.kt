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

            // Verify phase 1 set phase1Completed flag
            val questionsJsonAfterPhase1 = File(fixturesDir, "build/questioner/questions.json")
            questionsJsonAfterPhase1.exists() shouldBe true
            val questionsAfterPhase1 = questionsJsonAfterPhase1.loadQuestionList()
            questionsAfterPhase1.size shouldBeGreaterThan 0
            val phase1CompletedCount = questionsAfterPhase1.count { it.phase1Completed }
            phase1CompletedCount shouldBe questionsAfterPhase1.size
            val validatedAfterPhase1 = questionsAfterPhase1.count { it.validated }
            validatedAfterPhase1 shouldBe 0 // Should not be validated yet

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

            // Verify phase 2 set validated flag
            val questionsAfterPhase2 = questionsJson.loadQuestionList()
            questionsAfterPhase2.size shouldBeGreaterThan 0
            val validatedCount = questionsAfterPhase2.count { it.validated }
            validatedCount shouldBe questionsAfterPhase2.size
            val phase1StillComplete = questionsAfterPhase2.count { it.phase1Completed }
            phase1StillComplete shouldBe questionsAfterPhase2.size

            // Verify calibration data is present
            questionsAfterPhase2.forEach { question ->
                val settings = question.testingSettings
                check(settings != null) { "Question ${question.published.name} missing testingSettings" }
                check(settings.executionCountLimit != null) { "Question ${question.published.name} missing executionCountLimit" }
                check(settings.allocationLimit != null) { "Question ${question.published.name} missing allocationLimit" }
            }

            // Copy to test resources for lib and server modules
            // Use two-phase validation results since they have accurate no-JIT calibration measurements
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
