package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.VERSION
import edu.illinois.cs.cs125.questioner.lib.loadQuestionList
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
                // executionCountLimit is non-nullable, so just verify allocationLimit is set
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

            // Verify report generation
            val summaryReport = File(fixturesDir, "build/questioner/validation-report.html")
            summaryReport.exists() shouldBe true

            val summaryContent = summaryReport.readText()
            summaryContent shouldContain "<!DOCTYPE html>"
            summaryContent shouldContain "Validation Summary"

            // Check that per-question reports exist and are linked
            val questionsDir = File(fixturesDir, "build/questioner/questions")
            questionsDir.exists() shouldBe true

            val questionDirs = questionsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            questionDirs.shouldNotBeEmpty()

            // Verify each question directory has a report.html
            questionDirs.forEach { dir ->
                val reportFile = File(dir, "report.html")
                reportFile.exists() shouldBe true

                val reportContent = reportFile.readText()
                reportContent shouldContain "<!DOCTYPE html>"
            }

            // Verify questions are displayed with proper format: "Name (author/slug)"
            questions.forEach { question ->
                val displayName = "${question.published.name} (${question.published.author}/${question.published.path})"
                summaryContent shouldContain question.published.name
                summaryContent shouldContain question.published.author
            }

            // Verify links in summary report are relative paths to question reports
            questionDirs.forEach { dir ->
                val hash = dir.name
                summaryContent shouldContain "questions/$hash/report.html"
            }

            // Verify external question from external/test was discovered and validated
            val externalQuestion = questions.find { it.published.name == "Add Two" }
            (externalQuestion != null).shouldBeTrue()
            externalQuestion!!.published.author shouldBe "external@illinois.edu"
            externalQuestion.phase1Completed.shouldBeTrue()
            externalQuestion.validated.shouldBeTrue()
        }

        "second validate run should be up-to-date".config(timeout = 600.seconds) {
            val fixturesDir = File("../plugin-fixtures")
            val repoPath = System.getProperty("com.autonomousapps.plugin-under-test.repo")
                ?: error("Missing system property: com.autonomousapps.plugin-under-test.repo")

            val initScript = createInitScript(repoPath)

            // First: clean validate to ensure fresh state
            val cleanResult = GradleRunner.create()
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

            cleanResult.task(":validate")?.outcome shouldBe TaskOutcome.SUCCESS

            // Second: run validate again - everything should be up-to-date
            val secondResult = GradleRunner.create()
                .withProjectDir(fixturesDir)
                .withArguments(
                    "--init-script",
                    initScript.absolutePath,
                    "validate",
                    "--stacktrace",
                )
                .forwardOutput()
                .build()

            // All subproject parse tasks should be up-to-date (split files prevent re-running)
            val parseTasks = secondResult.tasks.filter {
                it.path.contains(":question-") && it.path.endsWith(":parse")
            }
            parseTasks.size shouldBeGreaterThan 0
            parseTasks.forEach { task ->
                task.outcome shouldBe TaskOutcome.UP_TO_DATE
            }

            // All subproject validate tasks should be up-to-date
            val validateTasks = secondResult.tasks.filter {
                it.path.contains(":question-") && it.path.endsWith(":validate")
            }
            validateTasks.size shouldBeGreaterThan 0
            validateTasks.forEach { task ->
                task.outcome shouldBe TaskOutcome.UP_TO_DATE
            }
        }
    })
