package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.lib.Language
import edu.illinois.cs.cs125.questioner.plugin.parse.ParsedJavaFile
import edu.illinois.cs.cs125.questioner.plugin.parse.ParsedKotlinFile
import edu.illinois.cs.cs125.questioner.plugin.parse.parseDirectory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

fun Path.getCorrectFiles() = Files.walk(this)
    .filter { path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt") }
    .map { path -> path.toFile() }
    .filter { file ->
        file.readText().lines().any { it.trim().startsWith("import edu.illinois.cs.cs125.questioner.lib.Correct") }
    }
    .filter { file ->
        when {
            file.name.endsWith(".java") -> ParsedJavaFile(file).isCorrect
            file.name.endsWith(".kt") -> ParsedKotlinFile(file).isCorrect
            else -> false
        }
    }.map { file ->
        Paths.get(file.absolutePath)
    }.collect(Collectors.toList()).filterNotNull().toList()

val fixturesDirectory: Path = Path.of(object {}::class.java.getResource("/src/main/java/")!!.toURI())

class TestParseDirectory :
    StringSpec({
        "it should find correct files" {
            fixturesDirectory.getCorrectFiles() shouldHaveSize 4
        }
        "it should parse the addone directory" {
            fixturesDirectory.resolve("com/examples/addone/Question.java").parseDirectory(fixturesDirectory, null, true)
                .also { question ->
                    question.published.path shouldBe "add-one"
                    question.incorrectExamples shouldHaveSize 6
                    question.metadata!!.unusedFiles shouldBe emptySet()
                }
        }
        "it should parse the cougarfeliform directory" {
            fixturesDirectory.resolve("com/examples/cougarfeliform/Cougar.java").parseDirectory(fixturesDirectory, null, true)
                .also { question ->
                    question.published.path shouldBe "cougar-feliform"
                    question.commonFiles!! shouldHaveSize 1
                    question.metadata!!.unusedFiles shouldBe emptySet()
                }
        }
        "it should parse the withcommoncode directory" {
            fixturesDirectory.resolve("com/examples/testing/withcommoncode/Question.java")
                .parseDirectory(fixturesDirectory, null, true)
                .also { question ->
                    question.published.path shouldBe "with-common-code"
                    question.commonFiles!! shouldHaveSize 1
                    question.getCorrect(Language.java)!!.lines().none { line ->
                        line.startsWith("com.examples.testing.common.Value;")
                    } shouldBe true
                    question.question.contents.lines().none { line ->
                        line.startsWith("com.examples.testing.common.Value;")
                    } shouldBe true
                    question.alternativeSolutions.none { flatFile ->
                        flatFile.contents.lines().any { line -> line.startsWith("com.examples.testing.common.Value;") }
                    } shouldBe true
                }
        }
        "it should parse the switchexpression directory" {
            fixturesDirectory.resolve("com/examples/switch_expression/Question.java")
                .parseDirectory(fixturesDirectory, null, true)
        }
        "it should include version in the hash" {
            fixturesDirectory.resolve("com/examples/addone/Question.java").also { directory ->
                val first = directory.parseDirectory(fixturesDirectory, null, true)
                    .also { question ->
                        question.published.path shouldBe "add-one"
                        question.incorrectExamples shouldHaveSize 6
                        question.metadata!!.unusedFiles shouldBe emptySet()
                    }
                directory.parseDirectory(fixturesDirectory, null, true, questionerVersion = "FOO")
                    .also { question ->
                        first.published.contentHash shouldNotBe question.published.contentHash
                    }
            }
        }
    })
