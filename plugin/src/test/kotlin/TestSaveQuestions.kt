package edu.illinois.cs.cs125.questioner.plugin

import edu.illinois.cs.cs125.questioner.plugin.parse.CleanSpec
import edu.illinois.cs.cs125.questioner.plugin.parse.ParsedJavaFile
import edu.illinois.cs.cs125.questioner.plugin.parse.ParsedKotlinFile
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TestSaveQuestions : StringSpec(
    {
        "should parse a question file" {
            ParsedJavaFile(
                "Second.java",
                """
package examples;

import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.jenisol.core.NotNull;

/*
 * Here is a _description_.
 */
@Correct(
  name="Test",
  version="2020.6.0",
  author="challen@illinois.edu"
)
@Import(paths="examples.second, examples.first")
@Wrap
@SuppressWarnings({"rawtypes", "unchecked"})
@Tags({"here", "we", "go"})
public class Second {
  public void correct(@NotNull String input) { }
  public void also(@NotNull String input) { }
}
""".trim(),
            ).also { parsedFile ->
                parsedFile.packageName shouldBe "examples"
                parsedFile.className shouldBe "Second"
                parsedFile.fullName shouldBe "examples.Second"

                parsedFile.correct shouldNotBe null
                parsedFile.type shouldBe "Correct"
                parsedFile.wrapWith shouldBe "Second"

                parsedFile.correct!!.also {
                    it.name shouldBe "Test"
                    it.version shouldBe "2020.6.0"
                    it.author shouldBe "challen@illinois.edu"
                    it.description shouldBe "<p>Here is a <em>description</em>.</p>"
                }
                parsedFile.suppressions shouldBe listOf("rawtypes", "unchecked")
                parsedFile.tags shouldBe listOf("here", "we", "go")
            }
        }
        "should parse a question file with multiple suppressions" {
            ParsedJavaFile(
                "Second.java",
                """
package examples;

import edu.illinois.cs.cs125.questioner.lib.Correct;
import edu.illinois.cs.cs125.jenisol.core.NotNull;

/*
 * Here is a _description_.
 */
@Correct(
  name="Test",
  version="2020.6.0",
  author="challen@illinois.edu"
)
@Import(paths="examples.second, examples.first")
@Wrap
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Second {
  public void correct(@NotNull String input) { }
  public void also(@NotNull String input) { }
}
""".trim(),
            ).also { parsedFile ->
                parsedFile.packageName shouldBe "examples"
                parsedFile.className shouldBe "Second"
                parsedFile.fullName shouldBe "examples.Second"

                parsedFile.correct shouldNotBe null
                parsedFile.type shouldBe "Correct"
                parsedFile.wrapWith shouldBe "Second"

                parsedFile.correct!!.also {
                    it.name shouldBe "Test"
                    it.version shouldBe "2020.6.0"
                    it.author shouldBe "challen@illinois.edu"
                    it.description shouldBe "<p>Here is a <em>description</em>.</p>"
                }
                parsedFile.suppressions shouldBe listOf("rawtypes", "unchecked")
            }
        }
        "should parse a Kotlin alternate static solution" {
            ParsedKotlinFile(
                "Second.kt",
                """
@file:AlsoCorrect
@file:Suppress("test", "me")
package examples

import edu.illinois.cs.cs125.questioner.AlsoCorrect

/*
 * Test me _markdown_.
 */

fun correct() { }
""".trim(),
            ).also { parsedFile ->
                parsedFile.className shouldBe "SecondKt"
                parsedFile.isAlternateSolution shouldBe true
                parsedFile.toAlternateFile(CleanSpec()).also {
                    it.klass shouldBe "SecondKt"
                }
                parsedFile.comment shouldBe "Test me _markdown_."
                parsedFile.description shouldBe "<p>Test me <em>markdown</em>.</p>"
                parsedFile.suppressions shouldBe listOf("test", "me")
            }
        }
        "should parse a Kotlin alternate class solution" {
            ParsedKotlinFile(
                "Second.kt",
                """
package examples

import edu.illinois.cs.cs125.questioner.lib.AlsoCorrect

/*
 * Test me _markdown_.
 */
@AlsoCorrect
class Second {
  fun correct() { }
}
""".trim(),
            ).also { parsedFile ->
                parsedFile.className shouldBe "Second"
                parsedFile.isAlternateSolution shouldBe true
                parsedFile.toAlternateFile(CleanSpec()).also {
                    it.klass shouldBe "Second"
                }
                parsedFile.comment shouldBe "Test me _markdown_."
                parsedFile.description shouldBe "<p>Test me <em>markdown</em>.</p>"
            }
        }
        "should parse a Kotlin incorrect static submission" {
            ParsedKotlinFile(
                "Second.kt",
                """
@file:Incorrect(reason="test")

package examples

import edu.illinois.cs.cs125.questioner.lib.Incorrect
import edu.illinois.cs.cs125.questioner.lib.Starter

/*
 * Test me.
 */
fun incorrect() { }
""".trim(),
            ).also { parsedFile ->
                parsedFile.className shouldBe "SecondKt"
                parsedFile.incorrect shouldBe "test"
            }
        }
        /*
        "should reject duplicate questions" {
            shouldThrow<IllegalStateException> {
                listOf(
                    ParsedJavaFile(
                        "Example.java",
                        """
package examples;
/*
         * Here is a _description_.
         */
@Correct(name="Test", version="2020.6.0", author="challen@illinois.edu")
public class Example {
  public void correct() { }
}
""".trim(),
                    ),
                    ParsedJavaFile(
                        "Another.java",
                        """
package examples;
/*
         * Here is a _description_.
         */
@Correct(name="Test", version="2020.6.0", author="challen@illinois.edu")
public class Another {
  public void correct() { }
}
""".trim(),
                    ),
                ).findQuestions(listOf("Another.java"))
            }
        }
        "should reject nested questions" {
            shouldThrow<IllegalStateException> {
                listOf(
                    ParsedJavaFile(
                        "examples/first/Example.java",
                        """
package examples.first;
/*
         * Here is a _description_.
         */
@Correct(name="Test", version="2020.6.0", author="challen@illinois.edu")
public class Example {
  public void correct() { }
}
""".trim(),
                    ),
                    ParsedJavaFile(
                        "examples/first/another/Another.java",
                        """
package examples.first.second;
/*
         * Here is a _description_.
         */
@Correct(name="Another", version="2020.6.0", author="challen@illinois.edu")
public class Another {
  public void correct() { }
}
""".trim(),
                    ),
                ).findQuestions(listOf("examples/first/Example.java", "examples/first/another/Another.java"))
            }
        }
         */
    },
)
